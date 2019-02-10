package mapwriter.region;

import mapwriter.util.BlockColors;
import mapwriter.config.Config;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

public class ChunkRender {

    public static final byte FLAG_UNPROCESSED = 0;
    public static final byte FLAG_NON_OPAQUE = 1;
    public static final byte FLAG_OPAQUE = 2;

    // values that change how height shading algorithm works
    public static final double BRIGHTEN_EXP = 0.35;
    public static final double DARKEN_EXP = 0.35;
    public static final double BRIGHTEN_AMP = 0.7;
    public static final double DARKEN_AMP = 1.4;

    // calculate the color of a pixel by alpha blending the color of each
    // block
    // in a column until an opaque block is reached.
    // y is topmost block height to start rendering at.
    // for maps without a ceiling y is simply the height of the highest block in
    // that chunk.
    // for maps with a ceiling y is the height of the first non opaque block
    // starting from
    // the ceiling.
    //
    // for every block in the column starting from the highest:
    // - get the block color
    // - get the biome shading
    // - extract color components as doubles in the range [0.0, 1.0]
    // - the shaded block color is simply the block color multiplied
    // by the biome shading for each component
    // - this shaded block color is alpha blended with the running
    // color for this column
    //
    // so the final map color is an alpha blended stack of all the
    // individual shaded block colors in the sequence [yStart .. yEnd]
    //
    // note that the "front to back" alpha blending algorithm is used
    // rather than the more common "back to front".
    //
    public static int getColumnColor(BlockColors bc, MapChunk chunk, int x, int y, int z, int heightW, int heightN) {

        double a = 1.0;
        double r = 0.0;
        double g = 0.0;
        double b = 0.0;
        for (; y > 0; y--) {
            final IBlockState blockState = chunk.getBlockState(x, y, z);
            final int c1 = bc.getStateColor(blockState);
            int alpha = c1 >> 24 & 0xff;

            // this is the color that gets returned for air, so set aplha to 0
            // so the game continues to the next block in the colum
            if (c1 == -8650628) {
                alpha = 0;
            }

            // no need to process block if it is transparent
            if (alpha > 0) {

                final int c2 = bc.getColorModifier(blockState, Minecraft.getMinecraft().world, new BlockPos(x, y, z));

                // extract color components as normalized doubles
                final double c1A = alpha / 255.0;
                final double c1R = (c1 >> 16 & 0xff) / 255.0;
                final double c1G = (c1 >> 8 & 0xff) / 255.0;
                final double c1B = (c1 >> 0 & 0xff) / 255.0;

                // c2A is implicitly 1.0 (opaque)
                final double c2R = (c2 >> 16 & 0xff) / 255.0;
                final double c2G = (c2 >> 8 & 0xff) / 255.0;
                final double c2B = (c2 >> 0 & 0xff) / 255.0;

                // alpha blend and multiply
                r = r + a * c1A * c1R * c2R;
                g = g + a * c1A * c1G * c2G;
                b = b + a * c1A * c1B * c2B;
                a = a * (1.0 - c1A);
            }
            // break when an opaque block is encountered
            if (alpha == 255) {
                break;
            }
        }

        final double heightShading = getHeightShading(y, heightW, heightN);
        final int lightValue = chunk.getLightValue(x, y + 1, z);
        final double lightShading = lightValue / 15.0;
        final double shading = (heightShading + 1.0) * lightShading;

        // apply the shading
        r = Math.min(Math.max(0.0, r * shading), 1.0);
        g = Math.min(Math.max(0.0, g * shading), 1.0);
        b = Math.min(Math.max(0.0, b * shading), 1.0);

        // now we have our final RGB values as doubles, convert to a packed ARGB
        // pixel.
        return (y & 0xff) << 24 | ((int) (r * 255.0) & 0xff) << 16 | ((int) (g * 255.0) & 0xff) << 8 | (int) (b * 255.0) & 0xff;
    }

    // get the height shading of a pixel.
    // requires the pixel to the west and the pixel to the north to have their
    // heights stored in the alpha channel to work.
    // the "height" of a pixel is the y value of the first opaque block in
    // the block column that created the pixel.
    // height values of 0 and 255 are ignored as these are used as the clear
    // values for pixels.
    public static double getHeightShading(int height, int heightW, int heightN) {

        int samples = 0;
        int heightDiff = 0;

        if (heightW > 0 && heightW < 255) {
            heightDiff += height - heightW;
            samples++;
        }

        if (heightN > 0 && heightN < 255) {
            heightDiff += height - heightN;
            samples++;
        }

        double heightDiffFactor = 0.0;
        if (samples > 0) {
            heightDiffFactor = (double) heightDiff / (double) samples;
        }

        // emphasize small differences in height, but as the difference in
        // height increases,
        // don't increase so much
        if (Config.moreRealisticMap) {
            return Math.atan(heightDiffFactor) * 0.3;
        }

        return heightDiffFactor >= 0.0 ? Math.pow(heightDiffFactor * (1 / 255.0), BRIGHTEN_EXP) * BRIGHTEN_AMP : -Math.pow(-(heightDiffFactor * (1 / 255.0)), DARKEN_EXP) * DARKEN_AMP;
    }

    public static void renderSurface(BlockColors bc, MapChunk chunk, int[] pixels, int offset, int scanSize, boolean dimensionHasCeiling) {

        final int chunkMaxY = chunk.getMaxY();
        for (int z = 0; z < MapWriterChunk.SIZE; z++) {
            for (int x = 0; x < MapWriterChunk.SIZE; x++) {
                // for the nether dimension search for the first non-opaque
                // block below the ceiling.
                // cannot use y = chunkMaxY as the nether sometimes spawns
                // mushrooms above the ceiling height. this fixes the
                // rectangular grey areas (ceiling bedrock) on the nether map.
                int y;
                if (dimensionHasCeiling) {
                    for (y = 127; y >= 0; y--) {
                        final IBlockState blockState = chunk.getBlockState(x, y, z);
                        final int color = bc.getStateColor(blockState);
                        int alpha = color >> 24 & 0xff;

                        if (color == -8650628) {
                            alpha = 0;
                        }

                        if (alpha != 0xff) {
                            break;
                        }
                    }
                } else {
                    y = chunkMaxY - 1;
                }

                final int pixelOffset = offset + z * scanSize + x;
                pixels[pixelOffset] = getColumnColor(bc, chunk, x, y, z, getPixelHeightW(pixels, pixelOffset, scanSize), getPixelHeightN(pixels, pixelOffset, scanSize));
            }
        }
    }

    public static void renderUnderground(BlockColors bc, MapChunk chunk, int[] pixels, int offset, int scanSize, int startY, byte[] mask) {

        startY = Math.min(Math.max(0, startY), 255);
        for (int z = 0; z < MapWriterChunk.SIZE; z++) {
            for (int x = 0; x < MapWriterChunk.SIZE; x++) {

                // only process columns where the mask bit is set.
                // process all columns if mask is null.
                if (mask != null && mask[z * 16 + x] != FLAG_NON_OPAQUE) {
                    continue;
                }

                // get the last non transparent block before the first opaque
                // block searching
                // towards the sky from startY
                int lastNonTransparentY = startY;
                for (int y = startY; y < chunk.getMaxY(); y++) {
                    final IBlockState blockState = chunk.getBlockState(x, y, z);
                    final int color = bc.getStateColor(blockState);
                    int alpha = color >> 24 & 0xff;

                    if (color == -8650628) {
                        alpha = 0;
                    }

                    if (alpha == 0xff) {
                        break;
                    }
                    if (alpha > 0) {
                        lastNonTransparentY = y;
                    }
                }

                final int pixelOffset = offset + z * scanSize + x;
                pixels[pixelOffset] = getColumnColor(bc, chunk, x, lastNonTransparentY, z, getPixelHeightW(pixels, pixelOffset, scanSize), getPixelHeightN(pixels, pixelOffset, scanSize));
            }
        }
    }

    static int getPixelHeightN(int[] pixels, int offset, int scanSize) {

        return offset >= scanSize ? pixels[offset - scanSize] >> 24 & 0xff : -1;
    }

    static int getPixelHeightW(int[] pixels, int offset, int scanSize) {

        return (offset & scanSize - 1) >= 1 ? pixels[offset - 1] >> 24 & 0xff : -1;
    }
}
