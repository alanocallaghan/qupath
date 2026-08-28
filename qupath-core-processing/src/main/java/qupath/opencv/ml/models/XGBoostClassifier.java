package qupath.opencv.ml.models;

import java.util.HashMap;
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
import qupath.lib.plugins.parameters.ParameterList;
import qupath.opencv.tools.OpenCVTools;

public class XGBoostClassifier implements OpenCVStatModel {
    private static final Logger logger = LoggerFactory.getLogger(XGBoostClassifier.class);

    private boolean isTrained = false;
    private Booster booster;

    @Override
    public String toString() {
        return "XGBoost";
    }

    @Override
    public boolean supportsMissingValues() {
        return false;
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
        return false;
    }

    @Override
    public boolean supportsProbabilities() {
        return false;
    }

    @Override
    public ParameterList getParameterList() {
        return null;
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
            Map<String, Object> params = new HashMap<>() {
                {
                    put("device", "cuda");
                    put("nthread", 10);
                    put("objective", "multi:softmax");
                    put("num_class", trainData.getClassLabels().rows());
                }
            };
            booster = XGBoost.train(dmat, params, nround, watches, null, null);
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
    public void close() throws Exception {

    }
}
