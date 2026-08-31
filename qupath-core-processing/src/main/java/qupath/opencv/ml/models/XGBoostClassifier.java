package qupath.opencv.ml.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoost;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_ml;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.PixelType;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.opencv.tools.OpenCVTools;

public class XGBoostClassifier implements TrainableModel {
    private static final Logger logger = LoggerFactory.getLogger(XGBoostClassifier.class);

    private ParameterList params;
    private boolean isTrained = false;
    @JsonAdapter(BoosterTypeAdapter.class)
    private Booster booster;

    public XGBoostClassifier() {
        this.params = createParameterList();
    }

    private ParameterList createParameterList() {
        ParameterList pl = new ParameterList();
        pl.addIntParameter("nthread",
                "Number of threads to use for preprocessing XGBoost models.",
                1);
        pl.addChoiceParameter("booster",
                "Which booster to use. gbtree and dart use tree based models while gblinear uses linear functions.",
                "gbtree",
                List.of("gbtree", "gblinear", "dart"));
        pl.addChoiceParameter(
                "device",
                "Which device to use",
                "cpu",
                List.of("cpu", "gpu", "cuda")
        );
        pl.addChoiceParameter(
                "verbosity",
                "Verbosity level",
                0,
                List.of(0, 1, 2, 3)
        );
        pl.addDoubleParameter(
                "learning_rate",
                """
                Learning rate: Step size shrinkage used in update to prevent overfitting.
                """,
                0.3
        );
        pl.addDoubleParameter(
                "gamma",
                """
                Gamma: Minimum loss reduction required to make a further partition on a leaf node of the tree.
                """,
                0.3
        );
        pl.addIntParameter(
                "max_depth",
                """
                        Maximum depth of a tree; higher values make the model more complex. 0 indicates no limit.
                        """,
                6
        );
        pl.addDoubleParameter(
                "min_child_weight",
                """
                Minimum sum of instance weight (hessian) needed in a child. Larger values make the model more conservative.
                """,
                1
        );
        pl.addDoubleParameter(
                "max_delta_step",
                """
                Maximum delta step we allow each leaf output to be. 0 means no constraint,
                positive values can help making the update step more conservative.
                """,
                0
        );
        pl.addDoubleParameter(
                "subsample",
                """
                Subsample ratio of the training instances; can prevent overfitting
                0.5 means that XGBoost would randomly sample half of the training data prior to growing trees.
                """,
                1
        );
        pl.addChoiceParameter(
                "sampling_method",
                """
                The method to use to sample the training instances.
                uniform: each training instance has an equal probability of being selected.
                gradient_based: the selection probability for each training instance is proportional to the regularized absolute value of gradients
                """,
                "uniform",
                List.of("uniform", "gradient_based")
        );
        pl.addDoubleParameter(
                "colsample_bytree",
                """
                subsample ratio of columns when constructing each tree. Subsampling occurs once for every tree constructed.
                """,
                1
        );
        pl.addDoubleParameter(
                "colsample_bylevel",
                """
                subsample ratio of columns for each level. Subsampling occurs once for every new depth level reached in a tree.
                """,
                1
        );
        pl.addDoubleParameter(
                "colsample_bynode",
                """
                subsample ratio of columns for each node (split). Subsampling occurs once every time a new split is evaluated.
                """,
                1
        );
        pl.addDoubleParameter(
                "alpha",
                """
                L1 regularization term on weights.
                """,
                1
        );
        pl.addDoubleParameter(
                "lambda",
                """
                L2 regularization term on weights.
                """,
                1
        );
        pl.addChoiceParameter(
                "tree_method",
                """
                The tree construction algorithm used in XGBoost.
                auto: Same as the hist tree method.
                exact: Exact greedy algorithm. Enumerates all split candidates.
                approx: Approximate greedy algorithm using quantile sketch and gradient histogram.
                hist: Faster histogram optimized approximate greedy algorithm.
                """,
                "auto",
                List.of("auto", "exact", "approx", "hist")
        );
        pl.addDoubleParameter(
                "scale_pos_weight",
                """
                Control the balance of positive and negative weights, useful for unbalanced classes.
                A typical value to consider: sum(negative instances) / sum(positive instances)
                """,
                1
        );
        pl.addIntParameter(
                "num_parallel_tree",
                """
                Number of parallel trees constructed during each iteration. This option is used to support boosted random forest.
                """,
                1
        );
        pl.addChoiceParameter(
                "sample_type",
                """
                Type of sampling algorithm; only applicable for tree boosters.
                uniform: dropped trees are selected uniformly.
                weighted: dropped trees are selected in proportion to weight.
                """,
                "uniform",
                List.of("uniform", "weighted")
        );
        pl.addChoiceParameter(
                "normalize_type",
                """
                Type of normalization algorithm; only applicable for tree boosters.
                tree: new trees have the same weight of each of dropped trees.
                forest: new trees have the same weight of sum of dropped trees (forest).
                """,
                "tree",
                List.of("tree", "forest")
        );
        pl.addDoubleParameter(
                "rate_drop",
                """
                Dropout rate (a fraction of previous trees to drop during the dropout).
                Range [0.0, 1.0]
                """,
                1
        );
        pl.addDoubleParameter(
                "skip_drop",
                """
                Probability of skipping the dropout procedure during a boosting iteration. Range [0.0, 1.0]
                """,
                1
        );
        pl.addDoubleParameter(
                "skip_drop",
                """
                Probability of skipping the dropout procedure during a boosting iteration. Range [0.0, 1.0]
                """,
                1
        );
        return pl;
    }

    @Override
    public String toString() {
        return "XGBoost";
    }

    @Override
    public boolean supportsMissingValues() {
        return true;
    }

    @Override
    public String getName() {
        return "XGBoost";
    }

    @Override
    public boolean isTrained() {
        return isTrained;
    }

    @Override
    public boolean supportsMulticlass() {
        return true;
    }

    @Override
    public boolean supportsAutoUpdate() {
        return true;
    }

    @Override
    public boolean supportsProbabilities() {
        // todo can be true if using multi:softprob
        return false;
    }

    @Override
    public ParameterList getParameterList() {
        return params;
    }

    @Override
    public TrainData createTrainData(Mat samples, Mat targets, int nLabels, Mat weights, boolean doMulticlass) {
        if (doMulticlass && !supportsMulticlass())
            logger.warn("Multiclass classification requested, but not supported");
        if (weights == null || weights.empty())
            return TrainData.create(samples, opencv_ml.ROW_SAMPLE, targets);
        else
            return TrainData.create(samples, opencv_ml.ROW_SAMPLE, targets, null, null, weights, null);
    }

    @Override
    public void train(TrainData trainData) {
        Mat trainSamples = trainData.getTrainSamples();

        float[] data = OpenCVTools.extractFloats(trainSamples);
        int nrow = trainSamples.rows();
        int ncol = trainSamples.cols();

        Mat responses = trainData.getTrainResponses();
        float[] fResponses = OpenCVTools.extractFloats(responses);

        DMatrix dmat = null;
        try {
            dmat = new DMatrix(data, nrow, ncol);
            dmat.setLabel(fResponses);
            int nround = 10;
            DMatrix finalDmat = dmat;
            Map<String, DMatrix> watches = new HashMap<>() {
                {
                    put("train", finalDmat);
                }
            };
            Map<String, Object> xgParams = new HashMap<>() {
                {
                    putAll(params.getKeyValueParameters(true));
                    put("validate_parameters", true);
                    // todo set parameters from list
                    put("device", "cuda");
                    put("objective", "multi:softmax");
                    put("num_class", trainData.getClassLabels().rows());
                }
            };
            booster = XGBoost.train(dmat, xgParams, nround, watches, null, null);
            isTrained = true;
        } catch (XGBoostError e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void predict(Mat samples, Mat results, Mat probabilities) {

        float[] data = OpenCVTools.extractFloats(samples);
        int nrow = samples.rows();
        int ncol = samples.cols();

        try {
            DMatrix dmat = new DMatrix(data, nrow, ncol);
            float[][] predicts = booster.predict(dmat);
            results.create(nrow, 1, opencv_core.CV_32F);
            var indexer = results.createIndexer();
            for (int i = 0; i < predicts.length; i++) {
                float min = Float.MIN_VALUE;
                for (int j = 0; j < predicts[0].length; j++) {
                    if (predicts[i][j] > min) {
                        min = predicts[i][j];
                    }
                    indexer.putDouble(new long[]{i, 0}, min);
                }
            }
        } catch (XGBoostError e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public PixelType getOutputType(boolean requestProbabilities) {
        return PixelType.FLOAT32;
    }

    @Override
    public void close() throws Exception {

    }

    public static class BoosterTypeAdapter extends TypeAdapter<Booster> {
        @Override
        public void write(JsonWriter out, Booster booster) throws IOException {
            if (booster == null) {
                out.nullValue();
                return;
            }
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            try {
                booster.saveModel(outStream);
            } catch (XGBoostError e) {
                throw new RuntimeException(e);
            }
            try (JsonReader reader = new JsonReader(new StringReader(outStream.toString(StandardCharsets.UTF_8)))) {
                out.value(String.valueOf(JsonParser.parseReader(reader)));
            }
        }

        @Override
        public Booster read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            JsonElement el = JsonParser.parseReader(in);
            String jsonString = el.getAsString();
            byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
            try {
                return XGBoost.loadModel(bytes);
            } catch (XGBoostError e) {
                throw new RuntimeException(e);
            }
        }
    }

}
