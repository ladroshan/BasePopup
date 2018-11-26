package razerdp.basepopup;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import razerdp.util.log.LogTag;
import razerdp.util.log.PopupLogUtil;

/**
 * Created by 大灯泡 on 2017/12/25.
 * <p>
 * 旨在用来拦截keyevent、以及蒙层
 */
final class PopupDecorViewProxy extends ViewGroup {
    private static final String TAG = "HackPopupDecorView";
    private PopupTouchController mPopupTouchController;
    //模糊层
    private PopupMaskLayout mMaskLayout;
    private BasePopupHelper mHelper;

    private PopupDecorViewProxy(Context context) {
        this(context, null);
    }

    private PopupDecorViewProxy(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    private PopupDecorViewProxy(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public static PopupDecorViewProxy create(Context context, BasePopupHelper helper) {
        PopupDecorViewProxy result = new PopupDecorViewProxy(context);
        result.init(helper);
        return result;
    }

    private void init(BasePopupHelper helper) {
        mHelper = helper;
        setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        if (mHelper != null) {
            mMaskLayout = PopupMaskLayout.create(getContext(), mHelper);
            addViewInLayout(mMaskLayout, -1, new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
    }

    public void addPopupDecorView(View target) {
        if (target == null) {
            throw new NullPointerException("contentView不能为空");
        }
        if (getChildCount() == 2) {
            removeViewsInLayout(1, 1);
        }

        final ViewGroup.LayoutParams layoutParams = target.getLayoutParams();
        final int height;
        final int width;
        if (layoutParams != null) {
            width = layoutParams.width;
            height = layoutParams.height;
        } else {
            width = mHelper.getPopupViewWidth();
            height = mHelper.getPopupViewHeight();
        }
        addView(target, width, height);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            //蒙层给最大值
            if (child == mMaskLayout) {
                measureChild(child, MeasureSpec.makeMeasureSpec(getScreenWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getScreenHeight(), MeasureSpec.EXACTLY));
            } else {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
            }
        }
        setMeasuredDimension(getScreenWidth(), getScreenHeight());

    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child == mMaskLayout) {
                child.layout(l, t, r, b);
            } else {
                int width = child.getMeasuredWidth();
                int height = child.getMeasuredHeight();

                int gravity = mHelper.getPopupGravity();

                int childLeft;
                int childTop;

                switch (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = (r - l - width) >> 1;
                        break;
                    case Gravity.RIGHT:
                    case Gravity.LEFT:
                    default:
                        childLeft = 0;
                        break;
                }

                switch (gravity & Gravity.VERTICAL_GRAVITY_MASK) {
                    case Gravity.CENTER_VERTICAL:
                        childTop = (b - t - height) >> 1;
                        break;
                    case Gravity.TOP:
                        childTop = 0;
                        break;
                    case Gravity.BOTTOM:
                        childTop = b - t - height;
                        break;
                    default:
                        childTop = 0;
                        break;
                }
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mMaskLayout != null) {
            mMaskLayout.handleStart(-2);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mPopupTouchController != null) {
            return mPopupTouchController.onInterceptTouchEvent(ev);
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean intercept = mPopupTouchController != null && mPopupTouchController.onDispatchKeyEvent(event);
        if (intercept) return true;
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (getKeyDispatcherState() == null) {
                return super.dispatchKeyEvent(event);
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                final KeyEvent.DispatcherState state = getKeyDispatcherState();
                if (state != null) {
                    state.startTracking(event, this);
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                final KeyEvent.DispatcherState state = getKeyDispatcherState();
                if (state != null && state.isTracking(event) && !event.isCanceled()) {
                    if (mPopupTouchController != null) {
                        PopupLogUtil.trace(LogTag.i, TAG, "dispatchKeyEvent: >>> onBackPressed");
                        return mPopupTouchController.onBackPressed();
                    }
                }
            }
            return super.dispatchKeyEvent(event);
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mPopupTouchController != null) {
            if (mPopupTouchController.onTouchEvent(event)) {
                return true;
            }
        }
        final int x = (int) event.getX();
        final int y = (int) event.getY();

        if ((event.getAction() == MotionEvent.ACTION_DOWN)
                && ((x < 0) || (x >= getWidth()) || (y < 0) || (y >= getHeight()))) {
            if (mPopupTouchController != null) {
                PopupLogUtil.trace(LogTag.i, TAG, "onTouchEvent:[ACTION_DOWN] >>> onOutSideTouch");
                return mPopupTouchController.onOutSideTouch();
            }
        } else if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            if (mPopupTouchController != null) {
                PopupLogUtil.trace(LogTag.i, TAG, "onTouchEvent:[ACTION_OUTSIDE] >>> onOutSideTouch");
                return mPopupTouchController.onOutSideTouch();
            }
        }
        return super.onTouchEvent(event);
    }


    /**
     * 应用helper的设置到popup的params
     *
     * @param params
     * @return
     */
    private ViewGroup.LayoutParams applyHelper(ViewGroup.LayoutParams params, BasePopupHelper helper) {
        if (!(params instanceof WindowManager.LayoutParams) || helper == null) {
            return params;
        }
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) params;
        if (!helper.isInterceptTouchEvent()) {
            PopupLogUtil.trace(LogTag.i, TAG, "applyHelper  >>>  不拦截事件");
            p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            p.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        }
        if (helper.isFullScreen()) {
            PopupLogUtil.trace(LogTag.i, TAG, "applyHelper  >>>  全屏");
            p.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

            // FIXME: 2017/12/27 全屏跟SOFT_INPUT_ADJUST_RESIZE冲突，暂时没有好的解决方案
            p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
        }
        return p;
    }

    int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }
}
