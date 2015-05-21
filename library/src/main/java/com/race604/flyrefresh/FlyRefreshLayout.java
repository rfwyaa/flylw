package com.race604.flyrefresh;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.v4.view.VelocityTrackerCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Scroller;

import com.race604.utils.UIUtils;

/**
 * Created by Jing on 15/5/18.
 */
public class FlyRefreshLayout extends ViewGroup {

    private static final String TAG = FlyRefreshLayout.class.getCanonicalName();
    private static final boolean D = true;

    private final static int DEFAULT_EXPAND = UIUtils.dpToPx(40);
    private final static int DEFAULT_HEIGHT = 0;

    private int mHeaderHeight = DEFAULT_HEIGHT;
    private int mHeaderExpandHeight = DEFAULT_EXPAND;
    private int mHeaderShrinkHeight = DEFAULT_HEIGHT;
    private int mHeaderId = 0;
    private int mContentId = 0;

    private int mPagingTouchSlop;

    private MotionEvent mDownEvent;
    private MotionEvent mLastMoveEvent;
    private boolean mHasSendCancelEvent = false;
    private boolean mPreventForHorizontal = false;
    private boolean mDisableWhenHorizontalMove = false;

    private View mHeaderView;
    protected View mContent;
    protected HeaderController mHeaderController;
    private IScrollHandler mScrollHandler;

    private ScrollChecker mScrollChecker;
    private VelocityTracker mVelocityTracker;

    public FlyRefreshLayout(Context context) {
        super(context);
        init(context, null);
    }

    public FlyRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FlyRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public FlyRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {

        if (attrs != null) {
            TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.FlyRefreshLayout);
            mHeaderHeight = arr.getDimensionPixelOffset(R.styleable.FlyRefreshLayout_frv_header_height,
                    DEFAULT_HEIGHT);
            mHeaderExpandHeight = arr.getDimensionPixelOffset(R.styleable.FlyRefreshLayout_frv_header_expand_height,
                    DEFAULT_EXPAND);
            mHeaderShrinkHeight = arr.getDimensionPixelOffset(R.styleable.FlyRefreshLayout_frv_header_shrink_height,
                    DEFAULT_HEIGHT);

            mHeaderId = arr.getResourceId(R.styleable.FlyRefreshLayout_frv_header, mHeaderId);
            mContentId = arr.getResourceId(R.styleable.FlyRefreshLayout_frv_content, mContentId);

            arr.recycle();
        }

        mHeaderController = new HeaderController(mHeaderHeight, mHeaderExpandHeight, mHeaderShrinkHeight);
        final ViewConfiguration conf = ViewConfiguration.get(getContext());
        mPagingTouchSlop = conf.getScaledTouchSlop() * 2;
        mScrollHandler = new DefalutScrollHandler();

        mScrollChecker = new ScrollChecker();
    }

    public void setScrollHandler(IScrollHandler handler) {
        mScrollHandler = handler;
    }

    @Override
    protected void onFinishInflate() {
        final int childCount = getChildCount();
        if (childCount > 2) {
            throw new IllegalStateException("FlyRefreshLayout only can host 2 elements");
        } else if (childCount == 2) {
            if (mHeaderId != 0 && mHeaderView == null) {
                mHeaderView = findViewById(mHeaderId);
            }

            if (mContentId != 0 && mContent == null) {
                mContent = findViewById(mContentId);
            }

            // not specify header or content
            if (mContent == null || mHeaderView == null) {

                View child1 = getChildAt(0);
                View child2 = getChildAt(1);
                if (child1 instanceof IFlyPullable) {
                    mHeaderView = child1;
                    mContent = child2;
                } else if (child2 instanceof IFlyPullable) {
                    mHeaderView = child2;
                    mContent = child1;
                } else {
                    // both are not specified
                    if (mContent == null && mHeaderView == null) {
                        mHeaderView = child1;
                        mContent = child2;
                    }
                    // only one is specified
                    else {
                        if (mHeaderView == null) {
                            mHeaderView = mContent == child1 ? child2 : child1;
                        } else {
                            mContent = mHeaderView == child1 ? child2 : child1;
                        }
                    }
                }
            }
        } else if (childCount == 1) {
            mContent = getChildAt(0);
        }

        super.onFinishInflate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mHeaderView != null) {
            measureChildWithMargins(mHeaderView, widthMeasureSpec, 0, heightMeasureSpec, 0);
        }

        if (mContent != null) {
            measureChildWithMargins(mContent, widthMeasureSpec, 0, heightMeasureSpec, mHeaderShrinkHeight);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        layoutChildren();
    }

    private void layoutChildren() {
        int offsetY = mHeaderController.getCurrentPos();
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();

        if (mHeaderView != null) {
            MarginLayoutParams lp = (MarginLayoutParams) mHeaderView.getLayoutParams();
            final int left = paddingLeft + lp.leftMargin;
            final int top = paddingTop + lp.topMargin + offsetY - mHeaderHeight;
            final int right = left + mHeaderView.getMeasuredWidth();
            final int bottom = top + mHeaderView.getMeasuredHeight();
            mHeaderView.layout(left, top, right, bottom);
        }

        if (mContent != null) {
            MarginLayoutParams lp = (MarginLayoutParams) mContent.getLayoutParams();
            final int left = paddingLeft + lp.leftMargin;
            final int top = paddingTop + lp.topMargin + offsetY;
            final int right = left + mContent.getMeasuredWidth();
            final int bottom = top + mContent.getMeasuredHeight();
            mContent.layout(left, top, right, bottom);
        }
    }

    private void sendCancelEvent() {
        MotionEvent last = mDownEvent;
        last = mLastMoveEvent;
        MotionEvent e = MotionEvent.obtain(last.getDownTime(),
                last.getEventTime() + ViewConfiguration.getLongPressTimeout(),
                MotionEvent.ACTION_CANCEL, last.getX(), last.getY(), last.getMetaState());
        super.dispatchTouchEvent(e);
    }

    private void sendDownEvent() {
        final MotionEvent last = mLastMoveEvent;
        MotionEvent e = MotionEvent.obtain(last.getDownTime(), last.getEventTime(),
                MotionEvent.ACTION_DOWN, last.getX(), last.getY(), last.getMetaState());
        super.dispatchTouchEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isEnabled() || mContent == null || mHeaderView == null) {
            return super.dispatchTouchEvent(ev);
        }

        int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mHeaderController.onTouchRelease();
                if (mHeaderController.isInTouch()) {
                    if (mHeaderController.hasMoved()) {
                        sendCancelEvent();
                        return true;
                    }
                    return super.dispatchTouchEvent(ev);
                } else {
                    return super.dispatchTouchEvent(ev);
                }

            case MotionEvent.ACTION_DOWN:
                mHasSendCancelEvent = false;
                mDownEvent = ev;
                mHeaderController.onTouchDown(ev.getX(), ev.getY());

                mScrollChecker.abortIfWorking();

                mPreventForHorizontal = false;
                if (mHeaderController.isInTouch()) {
                    // do nothing, intercept child event
                } else {
                    super.dispatchTouchEvent(ev);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                mLastMoveEvent = ev;
                mHeaderController.onTouchMove(ev.getX(), ev.getY());

                float offsetX = mHeaderController.getOffsetX();
                float offsetY = mHeaderController.getOffsetY();

                if (mDisableWhenHorizontalMove && !mPreventForHorizontal
                        && (Math.abs(offsetX) > mPagingTouchSlop
                        && Math.abs(offsetX) > Math.abs(offsetY))) {
                    mPreventForHorizontal = true;
                }
                if (mPreventForHorizontal) {
                    return super.dispatchTouchEvent(ev);
                }

                boolean moveDown = offsetY > 0;

                if (moveDown) {
                    if (mContent != null && mScrollHandler.canScrollUp(mContent)) {
                        if (mHeaderController.isInTouch()) {
                            mHeaderController.onTouchRelease();
                            sendDownEvent();
                        }
                        return super.dispatchTouchEvent(ev);
                    } else {
                        if (!mHeaderController.isInTouch()) {
                            mHeaderController.onTouchDown(ev.getX(), ev.getY());
                            offsetY = mHeaderController.getOffsetY();
                        }
                        movePos(offsetY);
                        return true;
                    }
                } else {
                    if (mHeaderController.canMoveUp()) {
                        movePos(offsetY);
                        return true;
                    } else {
                        if (mHeaderController.isInTouch()) {
                            mHeaderController.onTouchRelease();
                            sendDownEvent();
                        }
                        return super.dispatchTouchEvent(ev);
                    }
                }

        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * if deltaY > 0, move the content down
     *
     * @param deltaY
     */
    private void movePos(float deltaY) {

        // has reached the top
        int delta = mHeaderController.willMove(deltaY);

        if (D) {
            Log.d(TAG, String.format("movePos deltaY = %s, delta = %d", deltaY, delta));
        }

        if (delta == 0) {
            return;
        }

        if (!mHasSendCancelEvent && mHeaderController.isInTouch()) {
            sendCancelEvent();
        }

        mContent.offsetTopAndBottom(delta);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    public static class LayoutParams extends MarginLayoutParams {

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        @SuppressWarnings({"unused"})
        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    class ScrollChecker implements Runnable {

        private int mLastFlingY;
        private Scroller mScroller;
        private boolean mIsRunning = false;
        private int mStart;
        private int mTo;

        public ScrollChecker() {
            mScroller = new Scroller(getContext());
        }

        @Override
        public void run() {
            boolean finish = !mScroller.computeScrollOffset() || mScroller.isFinished();
            int curY = mScroller.getCurrY();
            int deltaY = curY - mLastFlingY;

            if (!finish) {
                mLastFlingY = curY;
                movePos(deltaY);
                post(this);
            } else {
                finish();
            }
        }

        private void finish() {
            reset();
            //onPtrScrollFinish();
        }

        private void reset() {
            mIsRunning = false;
            mLastFlingY = 0;
            removeCallbacks(this);
        }

        public void abortIfWorking() {
            if (mIsRunning) {
                if (!mScroller.isFinished()) {
                    mScroller.forceFinished(true);
                }
                //onPtrScrollAbort();
                reset();
            }
        }

        public void tryToScrollTo(int to, int duration) {
            if (mHeaderController.isAlreadyHere(to)) {
                return;
            }
            mStart = mHeaderController.getCurrentPos();
            mTo = to;
            int distance = to - mStart;

            removeCallbacks(this);

            mLastFlingY = 0;

            // fix #47: Scroller should be reused, https://github.com/liaohuqiu/android-Ultra-Pull-To-Refresh/issues/47
            if (!mScroller.isFinished()) {
                mScroller.forceFinished(true);
            }
            mScroller.startScroll(0, 0, 0, distance, duration);
            post(this);
            mIsRunning = true;
        }
    }
}