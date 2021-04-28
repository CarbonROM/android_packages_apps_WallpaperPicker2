/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wallpaper.picker;

import android.app.WallpaperColors;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.Nullable;

import com.android.wallpaper.R;
import com.android.wallpaper.util.PreviewUtils;
import com.android.wallpaper.util.SurfaceViewUtils;

/** A surface holder callback that renders user's workspace on the passed in surface view. */
public class WorkspaceSurfaceHolderCallback implements SurfaceHolder.Callback {

    private static final String KEY_WALLPAPER_COLORS = "wallpaper_colors";
    private final SurfaceView mWorkspaceSurface;
    private final PreviewUtils mPreviewUtils;
    private final boolean mShouldUseWallpaperColors;

    private WallpaperColors mWallpaperColors;
    private boolean mIsWallpaperColorsReady;
    private Surface mLastSurface;
    private Message mCallback;

    private boolean mNeedsToCleanUp;

    public WorkspaceSurfaceHolderCallback(SurfaceView workspaceSurface, Context context) {
        this(workspaceSurface, context, false);
    }

    /**
     * Creates a new instance of {@link WorkspaceSurfaceHolderCallback} specifying if wallpaper
     * colors should be used to preview the workspace.
     *
     * @param shouldUseWallpaperColors if true, the workspace preview won't be requested until both
     *                                 the surface is created and wallpaper colors are set via
     *                                 {@link #setWallpaperColors(WallpaperColors)}
     */
    public WorkspaceSurfaceHolderCallback(SurfaceView workspaceSurface, Context context,
            boolean shouldUseWallpaperColors) {
        mWorkspaceSurface = workspaceSurface;
        mPreviewUtils = new PreviewUtils(context,
                context.getString(R.string.grid_control_metadata_name));
        mShouldUseWallpaperColors = shouldUseWallpaperColors;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mPreviewUtils.supportsPreview() && mLastSurface != holder.getSurface()) {
            mLastSurface = holder.getSurface();
            maybeRenderPreview();
        }
    }

    /**
     * Set the current wallpaper's colors. This method must be called  if this instance was created
     * with shouldUseWallpaperColors = true (even with {@code null} colors).
     *
     * @param colors WallpaperColors extracted from the current wallpaper preview, or {@code null}
     *               if none are available.
     * @see #WorkspaceSurfaceHolderCallback(SurfaceView, Context, boolean)
     */
    public void setWallpaperColors(@Nullable WallpaperColors colors) {
        mWallpaperColors = colors;
        mIsWallpaperColorsReady = true;
        maybeRenderPreview();
    }

    private void maybeRenderPreview() {
        if ((mShouldUseWallpaperColors && !mIsWallpaperColorsReady) || mLastSurface == null) {
            return;
        }
        Bundle result = requestPreview(mWorkspaceSurface);
        if (result != null) {
            mWorkspaceSurface.setChildSurfacePackage(
                    SurfaceViewUtils.getSurfacePackage(result));
            mCallback = SurfaceViewUtils.getCallback(result);

            if (mNeedsToCleanUp) {
                cleanUp();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) { }

    public void cleanUp() {
        if (mCallback != null) {
            try {
                mCallback.replyTo.send(mCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                mCallback = null;
            }
        } else {
            mNeedsToCleanUp = true;
        }
    }

    public void resetLastSurface() {
        mLastSurface = null;
    }

    protected Bundle requestPreview(SurfaceView workspaceSurface) {
        Bundle request = SurfaceViewUtils.createSurfaceViewRequest(workspaceSurface);
        if (mWallpaperColors != null) {
            request.putParcelable(KEY_WALLPAPER_COLORS, mWallpaperColors);
        }
        return mPreviewUtils.renderPreview(request);
    }
}
