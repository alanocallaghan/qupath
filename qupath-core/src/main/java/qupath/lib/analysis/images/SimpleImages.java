/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.analysis.images;


import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ComplexType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Create {@link SimpleImage SimpleImage} instances for basic pixel processing.
 * 
 * @author Pete Bankhead
 *
 */
public class SimpleImages {
	
	/**
	 * Get the pixel values for the image.
	 * @param image
	 * @param direct if true, return the direct pixel buffer if possible. The caller should <i>not</i> modify this.
	 * @return
	 */
	public static float[] getPixels(SimpleImage image, boolean direct) {
		if (image instanceof SimpleModifiableImage)
			return ((SimpleModifiableImage)image).getArray(direct);
		int n = image.getWidth() * image.getHeight();
		int w = image.getWidth();
		float[] pixels = new float[n];
		for (int i = 0; i < n; i++)
			pixels[i] = image.getValue(i % w, i / w);
		return pixels;
	}
	
	/**
	 * Create a {@link SimpleImage} backed by an existing float array of pixels.
	 * <p>
	 * Pixels are stored in row-major order.
	 * 
	 * @param data
	 * @param width
	 * @param height
	 * @return
	 */
	public static SimpleModifiableImage createFloatImage(float[] data, int width, int height) {
		return new FloatArraySimpleImage(data, width, height);
	}

	/**
	 * Create a {@link SimpleImage} backed by a float array of pixels.
	 *
	 * @param width
	 * @param height
	 * @return
	 */
	public static SimpleModifiableImage createFloatImage(int width, int height) {
		return new FloatArraySimpleImage(new float[width * height], width, height);
	}
	
	
	/**
	 * Implementation of a SimpleImage backed by an array of floats.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class FloatArraySimpleImage implements SimpleModifiableImage {

		private float[] data;
		private int width;
		private int height;
		
		public FloatArraySimpleImage(float[] data, int width, int height) {
			this.data = data;
			this.width = width;
			this.height = height;
		}
		
		public FloatArraySimpleImage(int width, int height) {
			this.data = new float[width * height];
			this.width = width;
			this.height = height;
		}
		
		@Override
		public float getValue(int x, int y) {
			return data[y * width + x];
		}

		@Override
		public void setValue(int x, int y, float val) {
			data[y * width + x] = val;
		}

		@Override
		public int getWidth() {
			return width;
		}

		@Override
		public int getHeight() {
			return height;
		}
		
		@Override
		public float[] getArray(boolean direct) {
			if (direct)
				return data;
			return data.clone();
		}

	}

	/**
	 * Implementation of a SimpleImage backed by an array of floats.
	 *
	 * @author Pete Bankhead
	 *
	 */
	static class RandomAccessibleIntervalSimpleImage<T extends ComplexType<T>> implements SimpleModifiableImage {
		private static final Logger logger = LoggerFactory.getLogger(RandomAccessibleIntervalSimpleImage.class);

		private RandomAccessibleInterval<T> rai;
		private RandomAccess<? extends ComplexType<?>> randomAccess;
		public RandomAccessibleIntervalSimpleImage(RandomAccessibleInterval<T> rai) {
			this.rai = rai;
			this.randomAccess = rai.randomAccess();
		}

		@Override
		public float getValue(int x, int y) {
			return randomAccess.setPositionAndGet(x, y).getRealFloat();
		}

		@Override
		public void setValue(int x, int y, float val) {
			randomAccess.setPositionAndGet(x, y).setReal(val);
		}

		@Override
		public int getWidth() {
			return (int)rai.dimension(0);
		}

		@Override
		public int getHeight() {
			return (int) rai.dimension(1);
		}

		@Override
		public float[] getArray(boolean direct) {
			if (direct) {
				logger.warn("Can't return direct pixel array from an RAI");
			}
			float[] data = new float[getWidth() * getHeight()];
			var counter = 0;
			for (int x = 0; x < getWidth(); x++) {
				for (int y = 0; y < getHeight(); y++) {
					data[counter++] = randomAccess.setPositionAndGet(x, y).getRealFloat();
				}
			}
			return data;
		}

	}
}