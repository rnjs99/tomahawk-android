/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2015, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.libtomahawk.collection;

import org.tomahawk.libtomahawk.resolver.Query;
import org.tomahawk.libtomahawk.resolver.Result;
import org.tomahawk.libtomahawk.resolver.ScriptResolver;

import android.database.Cursor;
import android.util.Log;
import android.util.SparseArray;

import java.util.List;

public class CollectionCursor<T> {

    private final static String TAG = CollectionCursor.class.getSimpleName();

    private int mSortMode;

    private List<T> mMergedItems;

    private SparseArray<Index> mIndex;

    private static class Index {

        int index;

        boolean fromMergedItems;
    }

    private SparseArray<T> mCursorCache = new SparseArray<>();

    private Cursor mCursor;

    private List<T> mItems;

    private Class<T> mClass;

    private ScriptResolver mScriptResolver;

    public CollectionCursor(Cursor cursor, Class<T> clss, ScriptResolver resolver) {
        mCursor = cursor;
        mClass = clss;
        mScriptResolver = resolver;
    }

    public CollectionCursor(List<T> items, Class<T> clss) {
        mItems = items;
        mClass = clss;
    }

    public void close() {
        if (mCursor != null) {
            mCursor.close();
        }
    }

    public T get(int location) {
        boolean fromMergedItems = false;
        if (mIndex != null) {
            Index index = mIndex.get(location);
            location = index.index;
            fromMergedItems = index.fromMergedItems;
        }
        if (fromMergedItems) {
            return mMergedItems.get(location);
        } else {
            return rawGet(location);
        }
    }

    private T rawGet(int location) {
        if (mCursor != null) {
            mCursor.moveToPosition(location);
            T cachedItem = mCursorCache.get(location);
            if (cachedItem == null) {
                if (mClass == Query.class) {
                    Artist artist = Artist.get(mCursor.getString(0));
                    Album album = Album.get(mCursor.getString(2), artist);
                    Track track = Track.get(mCursor.getString(3), album, artist);
                    track.setDuration(mCursor.getInt(4));
                    track.setAlbumPos(mCursor.getInt(7));
                    Result result = Result.get(mCursor.getString(5), track, mScriptResolver);
                    Query query = Query.get(result, false);
                    query.addTrackResult(result, 1.0f);
                    cachedItem = (T) query;
                } else if (mClass == Album.class) {
                    Artist artist = Artist.get(mCursor.getString(1));
                    Album album = Album.get(mCursor.getString(0), artist);
                    cachedItem = (T) album;
                } else if (mClass == Artist.class) {
                    Artist artist = Artist.get(mCursor.getString(0));
                    cachedItem = (T) artist;
                }
                mCursorCache.put(location, cachedItem);
            }
            return cachedItem;
        } else {
            return mItems.get(location);
        }
    }

    public int size() {
        if (mIndex != null) {
            return mIndex.size();
        } else if (mCursor != null) {
            return mCursor.getCount();
        } else {
            return mItems.size();
        }
    }

    public void mergeItems(int sortMode, List<T> items) {
        mMergedItems = items;
        mSortMode = sortMode;

        updateIndex();
    }

    private void updateIndex() {
        mIndex = new SparseArray<>();
        int size1 = mCursor != null ? mCursor.getCount() : mItems.size();
        int size2 = mMergedItems.size();
        int counter1 = 0;
        int counter2 = 0;
        int i = 0;
        while (counter1 < size1 || counter2 < size2) {
            int compareResult;
            if (counter1 < size1 && counter2 < size2) {
                compareResult =
                        getSortString(mMergedItems, counter2).compareTo(getSortString(counter1));
            } else if (counter1 >= size1) {
                compareResult = -1;
            } else {
                compareResult = 1;
            }
            if (compareResult > 0) {
                Index index = new Index();
                index.fromMergedItems = false;
                index.index = counter1++;
                mIndex.put(i++, index);
            } else if (compareResult < 0) {
                Index index = new Index();
                index.fromMergedItems = true;
                index.index = counter2++;
                mIndex.put(i++, index);
            } else {
                if (rawGet(counter1) != mMergedItems.get(counter2)) {
                    Index index = new Index();
                    index.fromMergedItems = true;
                    index.index = counter2++;
                    mIndex.put(i++, index);
                } else {
                    counter2++;
                }
                Index index = new Index();
                index.fromMergedItems = false;
                index.index = counter1++;
                mIndex.put(i++, index);
            }
        }

    }

    private String getSortString(int location) {
        if (mCursor != null) {
            mCursor.moveToPosition(location);
            if (mClass == Query.class) {
                if (mSortMode == Collection.SORT_ALPHA) {
                    return mCursor.getString(3);
                } else if (mSortMode == Collection.SORT_ARTIST_ALPHA) {
                    return mCursor.getString(0);
                } else if (mSortMode == Collection.SORT_LAST_MODIFIED) {
                    return mCursor.getString(3); //TODO
                }
            } else if (mClass == Album.class) {
                if (mSortMode == Collection.SORT_ALPHA) {
                    return mCursor.getString(0);
                } else if (mSortMode == Collection.SORT_ARTIST_ALPHA) {
                    return mCursor.getString(1);
                } else if (mSortMode == Collection.SORT_LAST_MODIFIED) {
                    return mCursor.getString(0); //TODO
                }
            } else if (mClass == Artist.class) {
                if (mSortMode == Collection.SORT_ALPHA) {
                    return mCursor.getString(0);
                } else if (mSortMode == Collection.SORT_LAST_MODIFIED) {
                    return mCursor.getString(0); //TODO
                }
            }
        } else {
            return getSortString(mItems, location);
        }
        Log.e(TAG, "getSortString(int location) - Couldn't return a string");
        return null;
    }

    private String getSortString(List<T> items, int location) {
        if (mSortMode == Collection.SORT_ALPHA) {
            AlphaComparable item = (AlphaComparable) items.get(location);
            return item.getName();
        } else if (mSortMode == Collection.SORT_ARTIST_ALPHA) {
            ArtistAlphaComparable item = (ArtistAlphaComparable) items.get(location);
            return item.getArtist().getName();
        } else if (mSortMode == Collection.SORT_LAST_MODIFIED) {
            AlphaComparable item = (AlphaComparable) items.get(location); //TODO
            return item.getName();
        }
        Log.e(TAG, "getSortString(List<T> items, int location) - Couldn't return a string");
        return null;
    }
}