/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.messaging.datamodel.media;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.text.TextUtils;

import com.android.messaging.R;
import com.android.messaging.util.Assert;
import com.android.messaging.util.AvatarUriUtil;

import java.io.IOException;
import java.util.List;

public class SimLableSelectorAvatarRequest extends AvatarRequest {
    private static Bitmap sRegularSimIcon;
    private String Sim = null;

    public SimLableSelectorAvatarRequest(final Context context,
            final AvatarRequestDescriptor descriptor , final String SimNumber) {
        super(context, descriptor);
        Sim = SimNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ImageResource loadMediaInternal(List<MediaRequest<ImageResource>> chainedTasks)
            throws IOException {
        Assert.isNotMainThread();
        final String avatarType = AvatarUriUtil.getAvatarType(mDescriptor.uri);
        final int width = mDescriptor.desiredWidth;
        final int height = mDescriptor.desiredHeight;
        final int simColor = Color.WHITE;
        return renderSimAvatarInternal(width, height, simColor);
    }

    private ImageResource renderSimAvatarInternal(final int width,final int height, final int subColor) {
        final Resources resources = mContext.getResources();
        final float halfWidth = width / 2;
        final float halfHeight = height / 2;
        final int minOfWidthAndHeight = Math.min(width, height);
        final int backgroundColor = resources.getColor(R.color.primary_color);
        final int textColor = resources.getColor(R.color.primary_color);
        final Bitmap bitmap = getBitmapPool().createOrReuseBitmap(width, height, backgroundColor);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Canvas canvas = new Canvas(bitmap);

        if (sRegularSimIcon == null) {
            final BitmapDrawable regularSim = (BitmapDrawable) mContext.getResources()
                    .getDrawable(R.drawable.ic_sim_card_send);
            sRegularSimIcon = regularSim.getBitmap();
        }

        paint.setColorFilter(new PorterDuffColorFilter(subColor, PorterDuff.Mode.SRC_ATOP));
        paint.setAlpha(0xff);
        canvas.drawBitmap(sRegularSimIcon, halfWidth - sRegularSimIcon.getWidth() / 2,
                halfHeight - sRegularSimIcon.getHeight() / 2, paint);
        paint.setColorFilter(null);
        paint.setAlpha(0xff);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setColor(textColor);
        final float letterToTileRatio =
                resources.getFraction(R.dimen.sim_identifier_to_tile_ratio, 1, 1);
        paint.setTextSize(letterToTileRatio * minOfWidthAndHeight);
        final Rect textBound = new Rect();
        paint.getTextBounds(Sim, 0, 1, textBound);

        final float xOffset = halfWidth - textBound.centerX();
        final float yOffset = halfHeight - textBound.centerY();
        canvas.drawText(Sim, xOffset, yOffset, paint);
        return new DecodedImageResource(getKey(), bitmap, ExifInterface.ORIENTATION_NORMAL);
    }

    @Override
    public int getCacheId() {
        return BugleMediaCacheManager.AVATAR_IMAGE_CACHE;
    }
}
