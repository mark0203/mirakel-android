/*
 * Copyright 2012 Lars Werkman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.larswerkman.colorpicker;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import de.azapps.mirakel.colorpicker.R;

public class SVBar extends View {

	/*
	 * Constants used to save/restore the instance state.
	 */
	private static final String STATE_PARENT = "parent";
	private static final String STATE_COLOR = "color";
	private static final String STATE_SATURATION = "saturation";
	private static final String STATE_VALUE = "value";

	/**
	 * The thickness of the bar.
	 */
	private int mBarThickness;

	/**
	 * The length of the bar.
	 */
	private int mBarLength;
	private int mPreferredBarLength;

	/**
	 * The radius of the pointer.
	 */
	private int mBarPointerRadius;

	/**
	 * The radius of the halo of the pointer.
	 */
	private int mBarPointerHaloRadius;

	/**
	 * The position of the pointer on the bar.
	 */
	private int mBarPointerPosition;

	/**
	 * {@code Paint} instance used to draw the bar.
	 */
	private Paint mBarPaint;

	/**
	 * {@code Paint} instance used to draw the pointer.
	 */
	private Paint mBarPointerPaint;

	/**
	 * {@code Paint} instance used to draw the halo of the pointer.
	 */
	private Paint mBarPointerHaloPaint;

	/**
	 * The rectangle enclosing the bar.
	 */
	private RectF mBarRect = new RectF();

	/**
	 * {@code Shader} instance used to fill the shader of the paint.
	 */
	private Shader shader;

	/**
	 * {@code true} if the user clicked on the pointer to start the move mode. <br>
	 * {@code false} once the user stops touching the screen.
	 * 
	 * @see #onTouchEvent(MotionEvent)
	 */
	private boolean mIsMovingPointer;

	/**
	 * The ARGB value of the currently selected color.
	 */
	private int mColor;

	/**
	 * An array of floats that can be build into a {@code Color} <br>
	 * Where we can extract the Saturation and Value from.
	 */
	private float[] mHSVColor = new float[3];

	/**
	 * Factor used to calculate the position to the Saturation/Value on the bar.
	 */
	private float mPosToSVFactor;

	/**
	 * Factor used to calculate the Saturation/Value to the postion on the bar.
	 */
	private float mSVToPosFactor;

	/**
	 * {@code ColorPicker} instance used to control the ColorPicker.
	 */
	private ColorPicker mPicker = null;

	public SVBar(Context context) {
		super(context);
		init(null, 0);
	}

	public SVBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs, 0);
	}

	public SVBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(attrs, defStyle);
	}

	private void init(AttributeSet attrs, int defStyle) {
		final TypedArray a = getContext().obtainStyledAttributes(attrs,
				R.styleable.ColorBars, defStyle, 0);
		final Resources b = getContext().getResources();

		this.mBarThickness = a.getDimensionPixelSize(
				R.styleable.ColorBars_bar_thickness,
				b.getDimensionPixelSize(R.dimen.bar_thickness));
		this.mBarLength = a.getDimensionPixelSize(
				R.styleable.ColorBars_bar_length,
				b.getDimensionPixelSize(R.dimen.bar_length));
		this.mPreferredBarLength = this.mBarLength;
		this.mBarPointerRadius = a.getDimensionPixelSize(
				R.styleable.ColorBars_bar_pointer_radius,
				b.getDimensionPixelSize(R.dimen.bar_pointer_radius));
		this.mBarPointerHaloRadius = a.getDimensionPixelSize(
				R.styleable.ColorBars_bar_pointer_halo_radius,
				b.getDimensionPixelSize(R.dimen.bar_pointer_halo_radius));

		a.recycle();

		this.mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		this.mBarPaint.setShader(this.shader);

		this.mBarPointerPosition = (this.mBarLength / 2)
				+ this.mBarPointerHaloRadius;

		this.mBarPointerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		this.mBarPointerHaloPaint.setColor(Color.BLACK);
		this.mBarPointerHaloPaint.setAlpha(0x50);

		this.mBarPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		this.mBarPointerPaint.setColor(0xff81ff00);

		this.mPosToSVFactor = 1 / ((float) this.mBarLength / 2);
		this.mSVToPosFactor = ((float) this.mBarLength / 2) / 1;

	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int intrinsicSize = this.mPreferredBarLength
				+ (this.mBarPointerHaloRadius * 2);

		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);

		int width;
		if (widthMode == MeasureSpec.EXACTLY) {
			width = widthSize;
		} else if (widthMode == MeasureSpec.AT_MOST) {
			width = Math.min(intrinsicSize, widthSize);
		} else {
			width = intrinsicSize;
		}

		this.mBarLength = width - (this.mBarPointerHaloRadius * 2);
		setMeasuredDimension(
				(this.mBarLength + (this.mBarPointerHaloRadius * 2)),
				(this.mBarPointerHaloRadius * 2));
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		this.mBarLength = w - (this.mBarPointerHaloRadius * 2);

		// Fill the rectangle instance.
		this.mBarRect.set(this.mBarPointerHaloRadius,
				(this.mBarPointerHaloRadius - (this.mBarThickness / 2)),
				(this.mBarLength + (this.mBarPointerHaloRadius)),
				(this.mBarPointerHaloRadius + (this.mBarThickness / 2)));

		// Update variables that depend of mBarLength.
		if (!isInEditMode()) {
			this.shader = new LinearGradient(this.mBarPointerHaloRadius, 0,
					(this.mBarLength + this.mBarPointerHaloRadius),
					this.mBarThickness, new int[] { 0xffffffff,
							Color.HSVToColor(this.mHSVColor), 0xff000000 },
					null, Shader.TileMode.CLAMP);
		} else {
			this.shader = new LinearGradient(this.mBarPointerHaloRadius, 0,
					(this.mBarLength + this.mBarPointerHaloRadius),
					this.mBarThickness, new int[] { 0xffffffff, 0xff81ff00,
							0xff000000 }, null, Shader.TileMode.CLAMP);
			Color.colorToHSV(0xff81ff00, this.mHSVColor);
		}

		this.mBarPaint.setShader(this.shader);
		this.mPosToSVFactor = 1 / ((float) this.mBarLength / 2);
		this.mSVToPosFactor = ((float) this.mBarLength / 2) / 1;
		float[] hsvColor = new float[3];
		Color.colorToHSV(this.mColor, hsvColor);
		if (hsvColor[1] < hsvColor[2]) {
			this.mBarPointerPosition = Math
					.round((this.mSVToPosFactor * hsvColor[1])
							+ this.mBarPointerHaloRadius);
		} else {
			this.mBarPointerPosition = Math
					.round((this.mSVToPosFactor * (1 - hsvColor[2]))
							+ this.mBarPointerHaloRadius
							+ (this.mBarLength / 2));
		}
		if (isInEditMode()) {
			this.mBarPointerPosition = (this.mBarLength / 2)
					+ this.mBarPointerHaloRadius;
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		// Draw the bar.
		canvas.drawRect(this.mBarRect, this.mBarPaint);
		// Draw the pointer halo.
		canvas.drawCircle(this.mBarPointerPosition, this.mBarPointerHaloRadius,
				this.mBarPointerHaloRadius, this.mBarPointerHaloPaint);
		// Draw the pointer.
		canvas.drawCircle(this.mBarPointerPosition, this.mBarPointerHaloRadius,
				this.mBarPointerRadius, this.mBarPointerPaint);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		getParent().requestDisallowInterceptTouchEvent(true);

		// Convert coordinates to our internal coordinate system
		float x = event.getX();

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			this.mIsMovingPointer = true;
			// Check whether the user pressed on the pointer
			if (x >= (this.mBarPointerHaloRadius)
					&& x <= (this.mBarPointerHaloRadius + this.mBarLength)) {
				this.mBarPointerPosition = Math.round(x);
				calculateColor(Math.round(x));
				this.mBarPointerPaint.setColor(this.mColor);
				invalidate();
			}
			break;
		case MotionEvent.ACTION_MOVE:
			if (this.mIsMovingPointer) {
				// Move the the pointer on the bar.
				if (x >= this.mBarPointerHaloRadius
						&& x <= (this.mBarPointerHaloRadius + this.mBarLength)) {
					this.mBarPointerPosition = Math.round(x);
					calculateColor(Math.round(x));
					this.mBarPointerPaint.setColor(this.mColor);
					if (this.mPicker != null) {
						this.mPicker.setNewCenterColor(this.mColor);
						this.mPicker.changeOpacityBarColor(this.mColor);
					}
					invalidate();
				} else if (x < this.mBarPointerHaloRadius) {
					this.mBarPointerPosition = this.mBarPointerHaloRadius;
					this.mColor = Color.WHITE;
					this.mBarPointerPaint.setColor(this.mColor);
					if (this.mPicker != null) {
						this.mPicker.setNewCenterColor(this.mColor);
						this.mPicker.changeOpacityBarColor(this.mColor);
					}
					invalidate();
				} else if (x > (this.mBarPointerHaloRadius + this.mBarLength)) {
					this.mBarPointerPosition = this.mBarPointerHaloRadius
							+ this.mBarLength;
					this.mColor = Color.BLACK;
					this.mBarPointerPaint.setColor(this.mColor);
					if (this.mPicker != null) {
						this.mPicker.setNewCenterColor(this.mColor);
						this.mPicker.changeOpacityBarColor(this.mColor);
					}
					invalidate();
				}
			}
			break;
		case MotionEvent.ACTION_UP:
			this.mIsMovingPointer = false;
			break;
		default:
			break;
		}
		return true;
	}

	/**
	 * Set the pointer on the bar. With the saturation value.
	 * 
	 * @param saturation
	 *            float between 0 > 1
	 */
	public void setSaturation(float saturation) {
		this.mBarPointerPosition = Math
				.round((this.mSVToPosFactor * saturation)
						+ this.mBarPointerHaloRadius);
		calculateColor(this.mBarPointerPosition);
		this.mBarPointerPaint.setColor(this.mColor);
		// Check whether the Saturation/Value bar is added to the ColorPicker
		// wheel
		if (this.mPicker != null) {
			this.mPicker.setNewCenterColor(this.mColor);
			this.mPicker.changeOpacityBarColor(this.mColor);
		}
		invalidate();
	}

	/**
	 * Set the pointer on the bar. With the Value value.
	 * 
	 * @param value
	 *            float between 0 > 1
	 */
	public void setValue(float value) {
		this.mBarPointerPosition = Math
				.round((this.mSVToPosFactor * (1 - value))
						+ this.mBarPointerHaloRadius + (this.mBarLength / 2));
		calculateColor(this.mBarPointerPosition);
		this.mBarPointerPaint.setColor(this.mColor);
		// Check whether the Saturation/Value bar is added to the ColorPicker
		// wheel
		if (this.mPicker != null) {
			this.mPicker.setNewCenterColor(this.mColor);
			this.mPicker.changeOpacityBarColor(this.mColor);
		}
		invalidate();
	}

	/**
	 * Set the bar color. <br>
	 * <br>
	 * Its discouraged to use this method.
	 * 
	 * @param color
	 */
	public void setColor(int color) {
		Color.colorToHSV(color, this.mHSVColor);
		this.shader = new LinearGradient(this.mBarPointerHaloRadius, 0,
				(this.mBarLength + this.mBarPointerHaloRadius),
				this.mBarThickness,
				new int[] { 0xffffffff, color, 0xff000000 }, null,
				Shader.TileMode.CLAMP);
		this.mBarPaint.setShader(this.shader);
		calculateColor(this.mBarPointerPosition);
		this.mBarPointerPaint.setColor(this.mColor);
		if (this.mPicker != null) {
			this.mPicker.setNewCenterColor(this.mColor);
			this.mPicker.changeOpacityBarColor(this.mColor);
		}
		invalidate();
	}

	/**
	 * Calculate the color selected by the pointer on the bar.
	 * 
	 * @param x
	 *            X-Coordinate of the pointer.
	 */
	private void calculateColor(int x) {
		if (x > (this.mBarPointerHaloRadius + (this.mBarLength / 2))
				&& x < (this.mBarPointerHaloRadius + this.mBarLength)) {
			this.mColor = Color
					.HSVToColor(new float[] {
							this.mHSVColor[0],
							1f,
							1 - (this.mPosToSVFactor * (x - (this.mBarPointerHaloRadius + (this.mBarLength / 2)))) });
		} else if (x > this.mBarPointerHaloRadius
				&& x < (this.mBarPointerHaloRadius + this.mBarLength)) {
			this.mColor = Color.HSVToColor(new float[] { this.mHSVColor[0],
					((this.mPosToSVFactor * (x - this.mBarPointerHaloRadius))),
					1f });
		} else if (x == this.mBarPointerHaloRadius) {
			this.mColor = Color.WHITE;
		} else if (x == this.mBarPointerHaloRadius + this.mBarLength) {
			this.mColor = Color.BLACK;
		}
	}

	/**
	 * Get the currently selected color.
	 * 
	 * @return The ARGB value of the currently selected color.
	 */
	public int getColor() {
		return this.mColor;
	}

	/**
	 * Adds a {@code ColorPicker} instance to the bar. <br>
	 * <br>
	 * WARNING: Don't change the color picker. it is done already when the bar
	 * is added to the ColorPicker
	 * 
	 * @see ColorPicker#addSVBar(SVBar)
	 * @param picker
	 */
	public void setColorPicker(ColorPicker picker) {
		this.mPicker = picker;
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();

		Bundle state = new Bundle();
		state.putParcelable(STATE_PARENT, superState);
		state.putFloatArray(STATE_COLOR, this.mHSVColor);
		float[] hsvColor = new float[3];
		Color.colorToHSV(this.mColor, hsvColor);
		if (hsvColor[1] < hsvColor[2]) {
			state.putFloat(STATE_SATURATION, hsvColor[1]);
		} else {
			state.putFloat(STATE_VALUE, hsvColor[2]);
		}

		return state;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle savedState = (Bundle) state;

		Parcelable superState = savedState.getParcelable(STATE_PARENT);
		super.onRestoreInstanceState(superState);

		setColor(Color.HSVToColor(savedState.getFloatArray(STATE_COLOR)));
		if (savedState.containsKey(STATE_SATURATION)) {
			setSaturation(savedState.getFloat(STATE_SATURATION));
		} else {
			setValue(savedState.getFloat(STATE_VALUE));
		}
	}
}