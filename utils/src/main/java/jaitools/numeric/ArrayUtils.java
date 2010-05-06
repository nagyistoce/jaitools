/*
 * Copyright 2009-2010 Michael Bedward
 *
 * This file is part of jai-tools.
 *
 * jai-tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * jai-tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with jai-tools.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package jaitools.numeric;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Provides a number of array operations not present in the standard Java
 * {@linkplain Arrays} class.
 * <p>
 * Based on methods originally in the "BandCombine" operator ported from
 * the GeoTools library (<a href="http://geotools.org">http://geotools.org</a>)
 * and written by Martin Desruisseaux.
 *
 * @author Michael Bedward
 * @author Martin Desruisseaux
 * @since 1.1
 * @source $URL$
 * @version $Id$
 */
public class ArrayUtils {

    /**
     * Resize the given array, truncating or padding with zeroes as necessary.
     * If the length of the input array equals the requested length it will
     * be returned unchanged, otherwise a new array instance is created.
     *
     * @param array the array to resize
     * @param length requested length
     * @return a new array of the requested size
     */
    public static double[] resize(final double[] array, final int length) {
        return resize(array, length);
    }

    /**
     * Resize the given array, truncating or padding with zeroes as necessary.
     * If the length of the input array equals the requested length it will
     * be returned unchanged, otherwise a new array instance is created.
     *
     * @param array the array to resize
     * @param length requested length
     * @return a new array of the requested size
     */
    public static float[] resize(final float[] array, final int length) {
        return resize(array, length);
    }

    /**
     * Resize the given array, truncating or padding with zeroes as necessary.
     * If the length of the input array equals the requested length it will
     * be returned unchanged, otherwise a new array instance is created.
     *
     * @param array the array to resize
     * @param length requested length
     * @return a new array of the requested size
     */
    public static int[] resize(final int[] array, final int length) {
        return resize(array, length);
    }

    /**
     * Generic helper for the {@code resize} methods.
     *
     * @param array input array
     * @param newLength requested length
     * @return a new array of length {@code newLength} and with data from the input
     *         array truncated or padded as necessary; or the input array if its
     *         length is equal to {@code newLength}
     */
    private static <T> T doResize(final T array, final int newLength) {
        if (newLength <= 0) {
            throw new IllegalArgumentException("new array length must be > 0");
        }

        final int len = array == null ? 0 : Array.getLength(array);
        if (newLength != len) {
            T newArray = (T) Array.newInstance(array.getClass().getComponentType(), newLength);
            System.arraycopy(array, 0, newArray, 0, Math.min(len, newLength));
            return newArray;
        } else {
            return array;
        }
    }
}
