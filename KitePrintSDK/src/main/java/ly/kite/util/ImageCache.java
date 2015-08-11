/*****************************************************
 *
 * ImageCache.java
 *
 *
 * Modified MIT License
 *
 * Copyright (c) 2010-2015 Kite Tech Ltd. https://www.kite.ly
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The software MAY ONLY be used with the Kite Tech Ltd platform and MAY NOT be modified
 * to be used with any competitor platforms. This means the software MAY NOT be modified 
 * to place orders with any competitors to Kite Tech Ltd, all orders MUST go through the
 * Kite Tech Ltd platform servers. 
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 *****************************************************/

///// Package Declaration /////

package ly.kite.util;


///// Import(s) /////

import android.content.BroadcastReceiver;
import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.LinkedList;


///// Class Declaration /////

/*****************************************************
 *
 * This class implements a MRU-LRU image cache.
 *
 *****************************************************/
public class ImageCache implements IImageConsumer
  {
  ////////// Static Constant(s) //////////

  @SuppressWarnings( "unused" )
  private static final String  LOG_TAG = "ImageCache";


  ////////// Static Variable(s) //////////


  ////////// Member Variable(s) //////////

  private int                           mCapacityInBytes;

  private HashMap<Object,Holder>        mHolderTable;
  private LinkedList<Holder>            mHolderList;
  private int                           mSizeInBytes;

  private HashMap<Object,PendingImage>  mPendingTable;


  ////////// Static Initialiser(s) //////////


  ////////// Static Method(s) //////////


  ////////// Constructor(s) //////////

  public ImageCache( int capacityInBytes )
    {
    mCapacityInBytes = capacityInBytes;

    mHolderTable     = new HashMap<>();
    mHolderList      = new LinkedList<>();
    mSizeInBytes     = 0;

    mPendingTable    = new HashMap<>();
    }


  ////////// IImageConsumer Method(s) //////////

  /*****************************************************
   *
   * Called when the image is being downloaded.
   *
   *****************************************************/
  @Override
  public void onImageDownloading( Object key )
    {
    // Find the corresponding pending image

    PendingImage pendingImage = mPendingTable.remove( key );

    if ( pendingImage == null ) return;


    // Notify the end consumer
    pendingImage.consumer.onImageDownloading( key );
    }


  /*****************************************************
   *
   * Called when the image is available. Override this
   * in a child class if we want to do anything with
   * the image before caching it, but remember that it
   * is on the UI thread.
   *
   *****************************************************/
  @Override
  public void onImageAvailable( Object key, Bitmap bitmap )
    {
    // Find the corresponding pending image

    PendingImage pendingImage = mPendingTable.remove( key );

    if ( pendingImage == null ) return;


    // Store the image
    addImage( key, bitmap );


    // Pass the image to the end consumer
    pendingImage.consumer.onImageAvailable( key, bitmap );
    }


  ////////// Method(s) //////////

  /*****************************************************
   *
   * Returns an image, if it is in the cache, null otherwise.
   *
   *****************************************************/
  public Bitmap getImage( Object key )
    {
    // Try and find the image

    Holder holder = mHolderTable.get( key );

    if ( holder == null ) return ( null );


    // Move the image to the front of the MRU-LRU list before
    // we return the bitmap.

    mHolderList.remove( holder );
    mHolderList.addFirst( holder );

    return ( holder.bitmap );
    }


  /*****************************************************
   *
   * Stores a pending request.
   *
   *****************************************************/
  public void addPendingImage( Object key, IImageConsumer consumer )
    {
    mPendingTable.put( key, new PendingImage( key, consumer ) );
    }


  /*****************************************************
   *
   * Adds an image to the cache.
   *
   *****************************************************/
  public void addImage( Object key, Bitmap bitmap )
    {
    // Create a new holder for the image
    Holder newHolder = new Holder( key, bitmap );

    // Store the holder in the table and at the front of the list
    mHolderTable.put( key, newHolder );
    mHolderList.addFirst( newHolder );


    // Calculate the new size after the image has been added. If the size
    // exceeds the capacity, then remove images from the end until we get
    // back down within the capacity.

    mSizeInBytes += newHolder.approximateSizeInBytes;

    while ( mSizeInBytes > mCapacityInBytes )
      {
      // Get the least recently used image and remove it

      Holder lruHolder = mHolderList.removeLast();

      if ( lruHolder == null ) break;

      mHolderTable.remove( lruHolder.key );

      mSizeInBytes -= lruHolder.approximateSizeInBytes;
      }
    }



  ////////// Inner Class(es) //////////

  /*****************************************************
   *
   * Holds an image.
   *
   *****************************************************/
  private class Holder
    {
    Object  key;
    Bitmap  bitmap;
    int     approximateSizeInBytes;


    Holder( Object key, Bitmap bitmap )
      {
      this.key                    = key;
      this.bitmap                 = bitmap;

      // Calculate the approximate size in bytes. This only works if
      // the bitmap is not reconfigured.
      this.approximateSizeInBytes = bitmap.getByteCount();
      }
    }


  /*****************************************************
   *
   * A pending image request.
   *
   *****************************************************/
  private class PendingImage
    {
    Object          key;
    IImageConsumer  consumer;


    PendingImage( Object key, IImageConsumer consumer )
      {
      this.key      = key;
      this.consumer = consumer;
      }

    }

  }

