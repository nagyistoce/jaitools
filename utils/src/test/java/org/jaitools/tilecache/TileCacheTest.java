/* 
 *  Copyright (c) 2009-2011, Michael Bedward. All rights reserved. 
 *   
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met: 
 *   
 *  - Redistributions of source code must retain the above copyright notice, this  
 *    list of conditions and the following disclaimer. 
 *   
 *  - Redistributions in binary form must reproduce the above copyright notice, this 
 *    list of conditions and the following disclaimer in the documentation and/or 
 *    other materials provided with the distribution.   
 *   
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR 
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON 
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */   

package org.jaitools.tilecache;

import java.io.File;

import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.TileCache;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for DiskMemTileCache
 * 
 * @author Michael Bedward
 * @since 1.0
 * @version $Id$
 */
public class TileCacheTest {

    private static TileCache origCache;
    private DiskMemTileCache cache;
    private TileCacheTestHelper helper;

    private static final float FLOAT_TOL = 0.0001F;

    @BeforeClass
    public static void commonSetup() {
        origCache = JAI.getDefaultInstance().getTileCache();

    }

    @AfterClass
    public static void commonCleanup() {
        JAI.getDefaultInstance().setTileCache(origCache);
    }
    
    @Before
    public void setup() {
        cache = new DiskMemTileCache();
        helper = new TileCacheTestHelper();
        JAI.getDefaultInstance().setTileCache(cache);
        
    }

    @After
    public void cleanup() {
        cache.flush();
    }

    /**
     * Test that JAI has our custom cache set as the default cache
     */
    @Test
    public void testJAIHasCache() {
        System.out.println("   JAI set with DiskMemTileCache");
        TileCache jaicache = JAI.getDefaultInstance().getTileCache();
        assertTrue(jaicache instanceof DiskMemTileCache);
    }

    /**
     * Test that the cache was set with the default parameters
     * correctly
     */
    @Test
    public void testDefaultParams() {
        System.out.println("   default cache params");

        assertTrue(cache.getMemoryCapacity() == DiskMemTileCache.DEFAULT_MEMORY_CAPACITY);
        assertTrue(cache.getMemoryThreshold() - DiskMemTileCache.DEFAULT_MEMORY_THRESHOLD < FLOAT_TOL);
        assertTrue(cache.getAutoFlushMemoryInterval() == DiskMemTileCache.DEFAULT_AUTO_FLUSH_MEMORY_INTERVAL);
        assertFalse(cache.isAutoFlushMemoryEnabled());
    }


    /**
     * Test that the cache is used correctly in a simple JAI operation
     */
    @Test
    public void testCacheUsed() {
        System.out.println("   cache use in simple op");

        /*
         * Create a rendering chain for an output image 3 tiles x 2 tiles
         */
        RenderedOp op = helper.simpleJAIOp(3, 2);

        /*
         * Reset the cache's memory capacity for resident tiles so
         * that it is enough for 3 tiles only
         */
        cache.setMemoryCapacity(helper.getTileMemSize() * 3);

        /*
         * Force computation of tiles. This will cause the cache to
         * be used
         */
        op.getTiles();

        /*
         * Test that the cache has all tiles but that only 3 are
         * resident in memory
         */
        assertTrue(cache.getNumTiles() == 6);
        assertTrue(cache.getNumResidentTiles() == 3);
    }

    /**
     * Test the caches ability to swap tiles in and out of limited memory
     */
    @Test
    public void testMemorySwapping() {
        System.out.println("   swapping tiles into memory");

        /*
         * Create a rendering chain for an output image 3 tiles x 2 tiles
         */
        RenderedOp op = helper.simpleJAIOp(3, 2);

        /*
         * Reset the cache's memory capacity for resident tiles so
         * that it is enough for 3 tiles only
         */
        cache.setMemoryCapacity(helper.getTileMemSize() * 3);

        helper.startObserving(cache);

        /*
         * Force computation of tiles. This will cause the cache to
         * be used. The first three tiles will fit into available
         * cache memory while the remaining three will only be in
         * the cache's disk storage.
         */
        op.getTiles();

        /*
         * Request the non-resident tiles to force memory swapping
         */
        boolean[] resident = new boolean[helper.getTiles().size()];
        int k = 0;
        for (DiskCachedTile tile : helper.getTiles()) {
            resident[k++] = helper.getResidentTiles().contains(tile);
        }

        k = 0;
        for (DiskCachedTile tile : helper.getTiles()) {
            if (!resident[k++]) {
                int x = tile.getTileX ();
                int y = tile.getTileY();
                op.getTile(x, y);
                assertTrue(helper.getResidentTiles().contains(tile));
            }
        }

        helper.stopObserving(cache);
    }

    /**
     * Test removing tiles for a given image
     */
    @Test
    public void removeTilesForImage() {
        System.out.println("   removal of tiles for an image");

        helper.startObserving(cache);

        /*
         * Create an image and use getTiles to force the tiles to be cached
         */
        RenderedOp op1 = helper.simpleJAIOp(2, 2);
        op1.getTiles();

        /*
         * Repeat for a second image
         */
        RenderedOp op2 = helper.simpleJAIOp(2, 2);
        op2.getTiles();

        /*
         * Remove tiles for the first image and check
         * that only tiles for the second image remain
         */
        cache.removeTiles(op1.getCurrentRendering());
        assert(cache.getNumTiles() == 4);
        for (DiskCachedTile tile : helper.getTiles()) {
            assertTrue(tile.getOwner() == op2.getCurrentRendering());
        }

        helper.stopObserving(cache);
    }

    /**
     * Test flushing the cache
     */
    @Test
    public void testFlush() {
        System.out.println("   cache flush and flushMemory");

        /*
         * Create an image and use getTiles to force the tiles to be cached
         */
        RenderedOp op = helper.simpleJAIOp(2, 2);
        op.getTiles();

        assertTrue(cache.getNumTiles() == 4);

        cache.flushMemory();
        assertTrue(cache.getNumTiles() == 4);
        assertTrue(cache.getNumResidentTiles() == 0);

        cache.flush();
        assertTrue(cache.getNumTiles() == 0);
    }

    /**
     * Test auto-flushing of resident tiles
     */
    @Test
    public void testAutoFlush() {
        System.out.println("   cache auto-flush:");

        RenderedOp op = helper.simpleJAIOp(2, 2);
        cache.setAutoFlushMemoryInterval(100);
        cache.setAutoFlushMemoryEnabled(true);
        
        cache.addObserver(helper);
        
        for (int i = 0; i < 5; i++) {
            System.out.println(String.format("    - cycle %d", i+1));

            op.getNewRendering().getTiles();
            assertTrue(cache.getNumResidentTiles() == 4);
            
            try {
                Thread.sleep(cache.getAutoFlushMemoryInterval() * 5);
            } catch (InterruptedException ex) {
                // ignore
            }
            assertTrue(cache.getNumResidentTiles() == 0);
        }
    }
    
    /**
     * Test that cache files are deleted when the associated tiles
     * are removed from the cache
     */
    @Test
    public void testFileHandling() {
        System.out.println("   cache file handling");

        helper.startObserving(cache);

        RenderedOp op = helper.simpleJAIOp(10, 10);
        File[] files = new File[cache.getNumTiles()];

        int k = 0;
        for (DiskCachedTile tile : helper.getTiles()) {
            File f = tile.getFile();
            assertTrue(f.exists());
            assertTrue(f.canRead());

            files[k++] = f;
        }

        cache.flush();

        for (File f : files) {
            assertFalse(f.exists());
        }

        helper.stopObserving(cache);
    }


}
