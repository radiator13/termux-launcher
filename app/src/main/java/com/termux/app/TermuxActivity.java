package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.github.mmin18.widget.RealtimeBlurView;
import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.launcher.animation.LauncherTransitionController;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.launcherctl.LauncherCtlApiServer;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.ShizukuBackend;
import com.termux.app.style.TermuxBackgroundManager;
import com.termux.app.style.TermuxSystemWallpaperManager;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection, SuggestionBarCallback {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;
    ExtraKeysView mExtraKeysView2;

    SuggestionBarView mSuggestionBarView;
    AzScrubRowView mAzScrubRowView;
    LauncherAzGestureFxView mLauncherAzGestureFxUnderlayView;
    LauncherAzGestureFxView mLauncherAzGestureFxOverlayView;

    private LauncherAppDataProvider mLauncherAppDataProvider;
    private LauncherConfigRepository mLauncherConfigRepository;
    private LauncherTransitionController mLauncherTransitionController;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys2;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The termux background manager for updating background.
     */
    TermuxBackgroundManager mTermuxBackgroundManager;

    /**
     * The termux system wallpaper manager for setting system wallpaper.
     */
    TermuxSystemWallpaperManager mTermuxSystemWallpaperManager;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();
    private final BroadcastReceiver mPackageChangeReceiver = new PackageChangeReceiver();
    private boolean mPackageChangeReceiverRegistered = false;
    @Nullable private LauncherApps mLauncherApps;
    @Nullable private LauncherApps.Callback mLauncherAppsCallback;
    private boolean mLauncherAppsCallbackRegistered = false;
    private static final long PACKAGE_REFRESH_DEBOUNCE_MS = 120L;
    private boolean mPackageRefreshForceCatalogReload = false;
    private final Runnable mPackageRefreshRunnable = () -> {
        boolean forceCatalogRefresh = mPackageRefreshForceCatalogReload;
        mPackageRefreshForceCatalogReload = false;
        refreshSuggestionBarFromPackageState(forceCatalogRefresh);
    };

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If service connected before activity became visible and bootstrap/session start should be retried onStart().
     */
    private boolean mPendingBootstrapOnStart = false;

    /**
     * Launch intent captured when bootstrap/session start is deferred to onStart().
     */
    @Nullable
    private Intent mPendingLaunchIntent;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;
    
    public boolean isToolbarHidden = false;

    private int mNavBarHeight;

    private float mTerminalToolbarDefaultHeight;
    private final Handler mAzGestureHandler = new Handler(Looper.getMainLooper());
    @Nullable private Runnable mPendingImeMarginApplyRunnable;
    private enum AzGestureMode {
        IDLE,
        AZ_TRACKING,
        UPWARD_LOCKED,
        ICON_TRACKING_LOCKED
    }
    @NonNull private AzGestureMode mAzGestureMode = AzGestureMode.IDLE;
    @Nullable private Runnable mAzEdgePagingRunnable;
    @Nullable private SuggestionBarView.AzDragFocusResult mAzCurrentFocusResult;
    @Nullable private Runnable mAzOverflowRefreshRunnable;
    private boolean mAzGestureActive = false;
    private boolean mSuggestionBarInteractionActive = false;
    private char mAzLockedLetter = '#';
    private int mAzLockedSelectionIndex = 0;
    private boolean mAzHasLockedSelection = false;
    private boolean mAzHasPreviewAnchor = false;
    private char mAzPreviewAnchorLetter = '#';
    private int mAzPreviewAnchorSelectionIndex = 0;
    private float mAzGestureDownTouchX = 0f;
    private float mAzGestureDownTouchY = 0f;
    private float mAzLastRawX = 0f;
    private float mAzLastRawY = 0f;
    private float mAzLastAnchorRawX = 0f;
    private float mAzLastAnchorRawY = 0f;
    private float mAzLockedAnchorRawX = 0f;
    private float mAzLockedAnchorRawY = 0f;
    private final RectF mAzRowRawBounds = new RectF();
    private final RectF mAppsRowRawBounds = new RectF();
    private final RectF mExtraKeysRawBounds = new RectF();
    private final RectF mAzFocusLetterRawBounds = new RectF();
    private final AzScrubRowView.LetterVisualMetrics mAzLetterVisualMetrics = new AzScrubRowView.LetterVisualMetrics();
    private static final long AZ_EDGE_PAGE_INITIAL_DELAY_MS = 180L;
    private static final long AZ_EDGE_PAGE_REPEAT_INTERVAL_MS = 260L;
    private static final long AZ_PREVIEW_TIMEOUT_REFRESH_MS = 5200L;
    private static final float AZ_UPWARD_LOCK_TOUCH_Y_RATIO = 0.45f;
    private static final float AZ_RETURN_TOUCH_Y_RATIO = 0.55f;
    private static final float AZ_UPWARD_DIRECTION_RATIO = 0.75f;

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;

    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;

    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 14;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;

    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;

    private static final int CONTEXT_MENU_STYLING_ID = 5;

    private static final int CONTEXT_SUBMENU_FONT_AND_COLOR_ID = 11;

    private static final int CONTEXT_SUBMENU_SET_BACKROUND_IMAGE_ID = 12;

    private static final int CONTEXT_SUBMENU_REMOVE_BACKGROUND_IMAGE_ID = 13;


    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;

    private static final int CONTEXT_MENU_HELP_ID = 7;

    private static final int CONTEXT_MENU_SETTINGS_ID = 8;

    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";

    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";
    private static volatile boolean sPendingStyleReloadOnNextResume = false;

    private static final int SUGGESTION_BAR_MIN_BUTTON_DP = 56;
    private static final int SUGGESTION_BAR_MAX_INPUT_CHARS = 10;
    private static final long EMPTY_SESSION_RECOVERY_DEBOUNCE_MS = 1500L;

    private int mStatusBarInsetTop;
    private boolean mSeamlessStatusBackgroundActive;
    private long mLastEmptySessionRecoveryElapsedMs;
    private int mImeBottomInsetPx;
    private long mLastImeMarginApplyTimeMs;
    private static final int IME_MARGIN_SMALL_THRESHOLD_DP = 16;
    private static final int IME_MARGIN_MAX_DP = 240;
    private static final int IME_MARGIN_JITTER_THRESHOLD_DP = 20;
    private static final long IME_MARGIN_APPLY_DEBOUNCE_MS = 120L;
    private static final long IME_MARGIN_APPLY_DELAY_MS = 90L;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;
        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);
        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);
        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        // Load preferences BEFORE setting theme (needed to check wallpaper preference)
        mPreferences = TermuxAppSharedPreferences.build(this, false);

        // Apply wallpaper or normal theme based on preference
        setActivityThemeAndWindow();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termux);
        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        if (mPreferences == null) {
            mPreferences = TermuxAppSharedPreferences.build(this, true);
        }
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }
        mPreferences.migrateTerminalMarginAdjustmentDefaultIfNeeded();
        mLauncherTransitionController = new LauncherTransitionController(this, mPreferences);
        setMargins();
        setSuggestionBarView();
        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());
        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            WindowInsetsCompat insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, v);
            mNavBarHeight = insetsCompat.getInsets(Type.systemBars()).bottom;
            mStatusBarInsetTop = insetsCompat.getInsets(Type.statusBars()).top;
            int imeInsetBottom = insetsCompat.getInsets(Type.ime()).bottom;
            mImeBottomInsetPx = Math.max(0, imeInsetBottom - mNavBarHeight);
            applyTerminalStatusBarInset(mSeamlessStatusBackgroundActive ? mStatusBarInsetTop : 0);
            if (mPreferences != null && mPreferences.isTerminalMarginAdjustmentEnabled() && shouldUseImeInsetsMarginAdjustment()) {
                scheduleImeDrivenRootBottomMarginApply(mImeBottomInsetPx);
            }
            return insetsCompat.toWindowInsets();
        });
        applySeamlessStatusBackgroundModeIfNeeded();
        ViewCompat.requestApplyInsets(content);
        if (mProperties.isUsingFullScreen()) {
            WindowInsetsController insetsController = getWindow().getInsetsController();
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.statusBars());
                insetsController.hide(WindowInsets.Type.navigationBars());
            }
        }
        // Must be done every time activity is created in order to registerForActivityResult,
        // Even if the logic of launching is based on user input.
        setBackgroundManager();
        setSystemWallpaperManager();
        setTermuxTerminalViewAndClients();
        setTerminalToolbarView(savedInstanceState);
        setSettingsButtonView();
        setNewSessionButtonView();
        setToggleKeyboardView();
        registerForContextMenu(mTerminalView);
        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);
        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            };

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this, getString(e.getMessage() != null && e.getMessage().contains("app is in background") ? R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general), true);
            mIsInvalidState = true;
            return;
        }
        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.maybeHandleGestureContract(intent, mSuggestionBarView);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Logger.logDebug(LOG_TAG, "onStart");
    
        if (mIsInvalidState) return;
    
        mIsVisible = true;

        if (mPendingBootstrapOnStart && mTermuxService != null && mTermuxService.isTermuxSessionsEmpty()) {
            mPendingBootstrapOnStart = false;
            Intent pendingIntent = mPendingLaunchIntent;
            mPendingLaunchIntent = null;
            startBootstrapAndSession(pendingIntent);
        }
        maybeRecoverFromEmptySession("onStart");
    
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();
    
        if (mPreferences.isTerminalMarginAdjustmentEnabled()) {
            if (shouldUseImeInsetsMarginAdjustment()) {
                removeTermuxActivityRootViewGlobalLayoutListener();
                applyImeDrivenRootBottomMargin(mImeBottomInsetPx);
            } else {
                addTermuxActivityRootViewGlobalLayoutListener();
            }
        }

        if (mPreferences.isMonetBackgroundEnabled()) {
            configureViewVisibility(R.id.terminal_monetbackground, true);
            applyTerminalMonetBackgroundOpacity();
        } else {
            configureViewVisibility(R.id.terminal_monetbackground, false);
        }
        configureBackgroundBlur(R.id.sessions_backgroundblur, R.id.sessions_background, mPreferences.isSessionsBlurEnabled(), 0.5f, mPreferences.getSessionsBlurRadius());
        configureExtraKeysBackground();
        applyTerminalBlurBackground();
        applyTerminalGrainOverlay();
        applySeamlessStatusBackgroundModeIfNeeded();
    
        registerTermuxActivityBroadcastReceiver();
        registerPackageChangeReceiver();
        registerLauncherAppsCallback();
        getWindow().getDecorView().post(() -> LauncherCtlApiServer.getInstance().ensureStartedAsync(getApplicationContext()));
        scheduleSuggestionBarPackageRefresh(true, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.logVerbose(LOG_TAG, "onResume");
        if (mIsInvalidState)
            return;
        if (consumePendingStyleReloadOnNextResume()) {
            reloadActivityStyling(true);
            return;
        }
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();
        maybeRecoverFromEmptySession("onResume");

        if (mPreferences.isMonetBackgroundEnabled()) {
            configureViewVisibility(R.id.terminal_monetbackground, true);
            applyTerminalMonetBackgroundOpacity();
        } else {
            configureViewVisibility(R.id.terminal_monetbackground, false);
        }
        configureBackgroundBlur(R.id.sessions_backgroundblur, R.id.sessions_background, mPreferences.isSessionsBlurEnabled(), 0.5f, mPreferences.getSessionsBlurRadius());
        configureExtraKeysBackground();
        applyTerminalBlurBackground();
        applyTerminalGrainOverlay();
        applySeamlessStatusBackgroundModeIfNeeded();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.post(this::updateAzOverflowAffordance);
        }

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);
        mIsOnResumeAfterOnCreate = false;
    }

    private void configureViewVisibility(int viewId, boolean isVisible) {
        View view = findViewById(viewId);
        view.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void applyTerminalMonetBackgroundOpacity() {
        int opacity = mPreferences.getTerminalBackgroundOpacity();
        float alpha = opacity / 100f;
        if (alpha < 0f) {
            alpha = 0f;
        } else if (alpha > 1f) {
            alpha = 1f;
        }
        View terminalMonetBackground = findViewById(R.id.terminal_monetbackground);
        if (terminalMonetBackground != null) {
            terminalMonetBackground.setAlpha(alpha);
        }
    }

    private void applyDecorViewBackgroundOpacity() {
        int opacity = mPreferences != null ? mPreferences.getTerminalBackgroundOpacity() : 50;
        if (opacity < 0) {
            opacity = 0;
        } else if (opacity > 100) {
            opacity = 100;
        }

        int alpha = (int) ((opacity / 100f) * 255);
        int backgroundColor = (alpha << 24) | 0x000000;

        View decorView = getWindow().getDecorView();
        if (decorView != null) {
            decorView.setBackgroundColor(backgroundColor);
            Logger.logVerbose(LOG_TAG, "Applied DecorView opacity: " + opacity + "% (alpha: 0x" + Integer.toHexString(alpha) + ")");
        }
    }


    private void configureBackgroundBlur(int blurViewId, int backgroundViewId, boolean isBlurEnabled, float alphaIfBlurred, int blurRadiusDp) {
        View blurView = findViewById(blurViewId);
        View backgroundView = findViewById(backgroundViewId);
        applyRealtimeBlurRadius(blurView, blurRadiusDp);
        blurView.setVisibility(isBlurEnabled ? View.VISIBLE : View.GONE);
        backgroundView.setAlpha(isBlurEnabled ? alphaIfBlurred : 1.0f);
    }
    
    private void configureExtraKeysBackground() {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        View appsBarViewPager = findViewById(R.id.apps_bar_viewpager);
        View extraKeysBackground = findViewById(R.id.extrakeys_background);
        View extraKeysBackgroundBlur = findViewById(R.id.extrakeys_backgroundblur);
        View bottomSpaceBackground = findViewById(R.id.activity_termux_bottom_space_background);
        View bottomSpaceBlur = findViewById(R.id.activity_termux_bottom_space_blur);
        View azRow = findViewById(R.id.apps_bar_az_row);
        View azFxUnderlay = findViewById(R.id.apps_bar_az_fx_underlay);
        View azFxOverlay = findViewById(R.id.apps_bar_az_fx_overlay);

        boolean isToolbarShown = mPreferences.shouldShowTerminalToolbar();
        boolean isBlurEnabled = mPreferences.isExtraKeysBlurEnabled();
        float barAlpha = mPreferences.getAppBarOpacity() / 100f;
        int blurRadiusDp = mPreferences.getExtraKeysBlurRadius();
        applyRealtimeBlurRadius(extraKeysBackgroundBlur, blurRadiusDp);
        applyRealtimeBlurRadius(bottomSpaceBlur, blurRadiusDp);
        // App bar uses the combined extra-keys background surface.

        if (!isToolbarShown) {
            updateViewVisibility(accessoryContainer, false);
            updateViewVisibility(extraKeysBackgroundBlur, false);
            updateViewVisibility(extraKeysBackground, false);
            updateViewVisibility(bottomSpaceBackground, false);
            updateViewVisibility(bottomSpaceBlur, false);
            updateViewVisibility(appsBarViewPager, false);
            updateViewVisibility(azRow, false);
            updateViewVisibility(azFxOverlay, false);
            updateViewVisibility(azFxUnderlay, false);
            return;
        }

        updateViewVisibility(accessoryContainer, true);
        updateViewVisibility(appsBarViewPager, true);
        if (azRow != null) {
            updateViewVisibility(azRow, mPreferences.isAppLauncherAzRowEnabled());
        }
        updateViewVisibility(azFxUnderlay, true);
        updateViewVisibility(azFxOverlay, true);

        if (extraKeysBackground != null) {
            updateViewVisibility(extraKeysBackground, true);
            updateViewAlpha(extraKeysBackground, isBlurEnabled ? barAlpha : 1.0f);
        }
        if (bottomSpaceBackground != null) {
            updateViewVisibility(bottomSpaceBackground, true);
            updateViewAlpha(bottomSpaceBackground, isBlurEnabled ? barAlpha : 1.0f);
        }

        if (extraKeysBackgroundBlur != null) {
            updateViewVisibility(extraKeysBackgroundBlur, isBlurEnabled);
        }
        if (bottomSpaceBlur != null) {
            updateViewVisibility(bottomSpaceBlur, isBlurEnabled);
        }
    }

    private void applyRealtimeBlurRadius(View blurView, int blurRadiusDp) {
        if (!(blurView instanceof RealtimeBlurView)) {
            return;
        }
        float radiusPx = ViewUtils.dpToPx(this, Math.max(0, blurRadiusDp));
        ((RealtimeBlurView) blurView).setBlurRadius(radiusPx);
    }

    private void applyRealtimeBlurDownsampleFactor(View blurView, int downsampleFactor) {
        if (!(blurView instanceof RealtimeBlurView)) {
            return;
        }
        ((RealtimeBlurView) blurView).setDownsampleFactor(Math.max(1, downsampleFactor));
    }

    private void applyTerminalBlurBackground() {
        if (mPreferences == null) {
            return;
        }
        View blurView = findViewById(R.id.terminal_backgroundblur);
        if (blurView == null) {
            return;
        }
        int blurRadiusDp = mPreferences.getTerminalBlurRadius();
        int downsampleFactor = mPreferences.getTerminalBlurDownsampleFactor();
        applyRealtimeBlurRadius(blurView, blurRadiusDp);
        applyRealtimeBlurDownsampleFactor(blurView, downsampleFactor);
        blurView.setVisibility(blurRadiusDp > 0 ? View.VISIBLE : View.GONE);
    }

    private void applyTerminalGrainOverlay() {
        View grainOverlay = findViewById(R.id.terminal_grain_overlay);
        if (!(grainOverlay instanceof TerminalGrainOverlayView) || mPreferences == null) {
            return;
        }
        int grainIntensity = mPreferences.getTerminalGrainIntensity();
        ((TerminalGrainOverlayView) grainOverlay).setIntensity(grainIntensity);
        grainOverlay.setVisibility(grainIntensity > 0 ? View.VISIBLE : View.GONE);
    }

    private boolean shouldEnableSeamlessStatusBackground() {
        if (mPreferences == null || mProperties == null || mProperties.isUsingFullScreen()) {
            return false;
        }
        return mPreferences.isMonetBackgroundEnabled()
            || mPreferences.getTerminalBlurRadius() > 0
            || mPreferences.getTerminalGrainIntensity() > 0;
    }

    private void applySeamlessStatusBackgroundModeIfNeeded() {
        boolean enable = shouldEnableSeamlessStatusBackground();
        if (mSeamlessStatusBackgroundActive == enable) {
            return;
        }
        mSeamlessStatusBackgroundActive = enable;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), !enable);

        if (mTermuxActivityRootView != null) {
            mTermuxActivityRootView.setClipToPadding(!enable);
            mTermuxActivityRootView.setClipChildren(!enable);
        }
        View terminalRootContainer = findViewById(R.id.terminal_root_container);
        if (terminalRootContainer instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) terminalRootContainer;
            container.setClipToPadding(!enable);
            container.setClipChildren(!enable);
        }

        applyTerminalStatusBarInset(enable ? mStatusBarInsetTop : 0);
        resetRootBottomMarginAfterEdgeModeToggle();
        View content = findViewById(android.R.id.content);
        if (content != null) {
            ViewCompat.requestApplyInsets(content);
        }
    }

    private void resetRootBottomMarginAfterEdgeModeToggle() {
        if (mTermuxActivityRootView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = mTermuxActivityRootView.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
            if (marginLayoutParams.bottomMargin != 0) {
                marginLayoutParams.bottomMargin = 0;
                mTermuxActivityRootView.setLayoutParams(marginLayoutParams);
            }
        }
        mTermuxActivityRootView.lastMarginBottomTime = 0L;
        mTermuxActivityRootView.lastMarginBottomExtraTime = 0L;
        mLastImeMarginApplyTimeMs = 0L;
        if (mPreferences != null && mPreferences.isTerminalMarginAdjustmentEnabled() && shouldUseImeInsetsMarginAdjustment()) {
            applyImeDrivenRootBottomMargin(mImeBottomInsetPx);
        }
    }

    private boolean shouldUseImeInsetsMarginAdjustment() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    private void applyImeDrivenRootBottomMargin(int imeBottomInsetPx) {
        if (mTermuxActivityRootView == null) {
            return;
        }

        int smallThresholdPx = (int) ViewUtils.dpToPx(this, IME_MARGIN_SMALL_THRESHOLD_DP);
        int maxMarginPx = (int) ViewUtils.dpToPx(this, IME_MARGIN_MAX_DP);
        int jitterThresholdPx = (int) ViewUtils.dpToPx(this, IME_MARGIN_JITTER_THRESHOLD_DP);

        int targetMargin = imeBottomInsetPx;
        if (targetMargin > 0 && targetMargin <= smallThresholdPx) {
            targetMargin = 0;
        } else if (targetMargin > maxMarginPx) {
            targetMargin = maxMarginPx;
        }

        ViewGroup.LayoutParams layoutParams = mTermuxActivityRootView.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
        int currentMargin = marginLayoutParams.bottomMargin;
        if (currentMargin == targetMargin) {
            return;
        }

        long now = System.currentTimeMillis();
        if (currentMargin > 0 && targetMargin > 0 &&
            Math.abs(currentMargin - targetMargin) <= jitterThresholdPx &&
            (now - mLastImeMarginApplyTimeMs) < IME_MARGIN_APPLY_DEBOUNCE_MS) {
            return;
        }

        marginLayoutParams.bottomMargin = targetMargin;
        mTermuxActivityRootView.setLayoutParams(marginLayoutParams);
        mLastImeMarginApplyTimeMs = now;

        if (mPreferences != null && mPreferences.isTerminalViewKeyLoggingEnabled()) {
            Logger.logVerbose(LOG_TAG, "IME margin apply: target=" + targetMargin +
                ", imeInset=" + imeBottomInsetPx + ", nav=" + mNavBarHeight);
        }
    }

    private void scheduleImeDrivenRootBottomMarginApply(int imeBottomInsetPx) {
        if (mPendingImeMarginApplyRunnable != null) {
            mAzGestureHandler.removeCallbacks(mPendingImeMarginApplyRunnable);
            mPendingImeMarginApplyRunnable = null;
        }

        final int targetInset = imeBottomInsetPx;
        Runnable runnable = () -> {
            mPendingImeMarginApplyRunnable = null;
            if (mPreferences != null && mPreferences.isTerminalMarginAdjustmentEnabled() && shouldUseImeInsetsMarginAdjustment()) {
                applyImeDrivenRootBottomMargin(targetInset);
            }
        };

        mPendingImeMarginApplyRunnable = runnable;
        mAzGestureHandler.postDelayed(runnable, IME_MARGIN_APPLY_DELAY_MS);
    }

    private void applyBackgroundLayerTopInset(int viewId, int insetTop) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
        int targetTopMargin = -Math.max(0, insetTop);
        if (marginLayoutParams.topMargin != targetTopMargin) {
            marginLayoutParams.topMargin = targetTopMargin;
            view.setLayoutParams(marginLayoutParams);
        }
    }

    private void applyTerminalStatusBarInset(int insetTop) {
        int safeInsetTop = Math.max(0, insetTop);
        applyBackgroundLayerTopInset(R.id.terminal_monetbackground, safeInsetTop);
        applyBackgroundLayerTopInset(R.id.terminal_backgroundblur, safeInsetTop);
        applyBackgroundLayerTopInset(R.id.terminal_grain_overlay, safeInsetTop);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logger.logDebug(LOG_TAG, "onStop");
        stopAzEdgePagingLoop();
        cancelAzOverflowRefresh();
        if (mIsInvalidState)
            return;
        mIsVisible = false;
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();
        removeTermuxActivityRootViewGlobalLayoutListener();
        unregisterTermuxActivityBroadcastReceiver();
        unregisterPackageChangeReceiver();
        unregisterLauncherAppsCallback();
        if (mPendingImeMarginApplyRunnable != null) {
            mAzGestureHandler.removeCallbacks(mPendingImeMarginApplyRunnable);
            mPendingImeMarginApplyRunnable = null;
        }
        mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logDebug(LOG_TAG, "onDestroy");
        if (mIsInvalidState)
            return;
        if (mSuggestionBarView != null) {
            mSuggestionBarView.releaseResources();
        }
        if (mTermuxBackgroundManager != null) {
            mTermuxBackgroundManager.destroy();
            mTermuxBackgroundManager = null;
        }
        if (mTermuxSystemWallpaperManager != null) {
            mTermuxSystemWallpaperManager.destroy();
            mTermuxSystemWallpaperManager = null;
        }
        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }
        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Logger.logVerbose(LOG_TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
        mTermuxTerminalSessionActivityClient.onConfigurationChanged(newConfig);
    }

    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");
        mTermuxService = ((TermuxService.LocalBinder) service).service;
        setTermuxSessionsListView();
        final Intent intent = getIntent();
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.maybeHandleGestureContract(intent, mSuggestionBarView);
        }
        setIntent(null);
        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                startBootstrapAndSession(intent);
            } else {
                // Service can connect before onStart() on some devices. Defer bootstrap/session creation.
                if (mIsOnResumeAfterOnCreate) {
                    mPendingBootstrapOnStart = true;
                    mPendingLaunchIntent = intent;
                } else {
                    // Service connected while activity is actually in background - bail out.
                    finishActivityIfNotFinishing();
                }
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }
        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
    }

    private void startBootstrapAndSession(@Nullable Intent intent) {
        TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
            // Bootstrap setup may complete after app startup; re-attempt launcher CLI script install.
            LauncherCtlApiServer.getInstance().ensureCliScriptsInstalled();

            // Activity might have been destroyed.
            if (mTermuxService == null)
                return;
            try {
                boolean launchFailsafe = false;
                if (intent != null && intent.getExtras() != null) {
                    launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                }
                mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
            } catch (WindowManager.BadTokenException e) {
                // Activity finished - ignore.
            }
        });
    }

    private void maybeRecoverFromEmptySession(@NonNull String source) {
        if (!mIsVisible || mTermuxService == null || mTermuxTerminalSessionActivityClient == null) {
            return;
        }
        if (!mTermuxService.isTermuxSessionsEmpty()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (mLastEmptySessionRecoveryElapsedMs > 0
            && (now - mLastEmptySessionRecoveryElapsedMs) < EMPTY_SESSION_RECOVERY_DEBOUNCE_MS) {
            return;
        }
        mLastEmptySessionRecoveryElapsedMs = now;
        Logger.logWarn(LOG_TAG, "No active terminal session while visible; attempting auto-recovery from " + source);
        startBootstrapAndSession(null);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");
        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }

    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }

    private void setActivityThemeAndWindow() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        Logger.logDebug(LOG_TAG, "Applied normal theme");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    private void setSuggestionBarView() {
        final ViewPager viewPager = findViewById(R.id.apps_bar_viewpager);
        mAzScrubRowView = findViewById(R.id.apps_bar_az_row);
        mLauncherAzGestureFxUnderlayView = findViewById(R.id.apps_bar_az_fx_underlay);
        mLauncherAzGestureFxOverlayView = findViewById(R.id.apps_bar_az_fx_overlay);
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setRenderLayer(LauncherAzGestureFxView.RenderLayer.UNDERLAY);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setRenderLayer(LauncherAzGestureFxView.RenderLayer.OVERLAY);
        }
        if (viewPager == null) {
            return;
        }
        viewPager.setClipChildren(false);
        viewPager.setClipToPadding(false);
        ViewParent vpParent = viewPager.getParent();
        if (vpParent instanceof ViewGroup) {
            ViewGroup parentGroup = (ViewGroup) vpParent;
            parentGroup.setClipChildren(false);
            parentGroup.setClipToPadding(false);
        }

        if (mPreferences != null) {
            if (mLauncherAppDataProvider == null) {
                mLauncherAppDataProvider = LauncherAppDataProvider.getInstance(this);
            }
            if (mLauncherConfigRepository == null) {
                mLauncherConfigRepository = new LauncherConfigRepository(mPreferences);
            }
        }

        // Set height based on preferences
        ViewGroup.LayoutParams layoutParams = viewPager.getLayoutParams();
        float barHeightScale = mPreferences.getAppLauncherBarHeightScale();
        int defaultHeight = (int) (getResources().getDisplayMetrics().density * 37.5f);
        layoutParams.height = Math.round(defaultHeight * barHeightScale);
        viewPager.setLayoutParams(layoutParams);

        final SuggestionBarCallback suggestionBarCallback = this;
        viewPager.setAdapter(new androidx.viewpager.widget.PagerAdapter() {
            @Override
            public int getCount() {
                return 1; // count of pages to scroll through
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup collection, int position) {
                LayoutInflater inflater = LayoutInflater.from(TermuxActivity.this);
                mSuggestionBarView = (SuggestionBarView) inflater.inflate(R.layout.suggestion_bar, collection, false);
                mSuggestionBarView.setAppDataProvider(mLauncherAppDataProvider);
                mSuggestionBarView.setConfigRepository(mLauncherConfigRepository);
                mSuggestionBarView.setAppCatalogChangedListener(TermuxActivity.this::syncAzScrubLettersAndTint);
                applySuggestionBarPreferences();
                mSuggestionBarView.reload();
                mTermuxTerminalViewClient.setSuggestionBarCallback(suggestionBarCallback);
                collection.addView(mSuggestionBarView);
                return mSuggestionBarView;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
                collection.removeView((View) view);
            }
        });

        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mTerminalView.requestFocus();
            }
        });

        if (mAzScrubRowView != null) {
            mAzScrubRowView.setScrubCallback(new AzScrubRowView.ScrubCallback() {
                @Override
                public void onScrub(char letter, int selectionIndex, float touchX, float touchY, float rawX, float rawY, @NonNull AzScrubRowView.GesturePhase phase) {
                    handleAzGestureScrub(letter, selectionIndex, touchX, touchY, rawX, rawY, phase);
                }

                @Override
                public void onCancel() {
                    resetAzGestureState(false, true);
                }

                @Override
                public void onDoubleTap() {
                    lockScreenFromAzDoubleTap();
                }
            });
        }
    }

    static int calculateSuggestionBarMaxButtons(DisplayMetrics displayMetrics) {
        if (displayMetrics == null) {
            return 1;
        }
        float density = Math.max(displayMetrics.density, 0.1f);
        int screenWidthDp = (int) (displayMetrics.widthPixels / density);
        return Math.max(1, screenWidthDp / SUGGESTION_BAR_MIN_BUTTON_DP);
    }

    private void applySuggestionBarPreferences() {
        if (mSuggestionBarView == null || mPreferences == null) {
            return;
        }
        if (mLauncherAppDataProvider == null) {
            mLauncherAppDataProvider = LauncherAppDataProvider.getInstance(this);
        }
        if (mLauncherConfigRepository == null) {
            mLauncherConfigRepository = new LauncherConfigRepository(mPreferences);
        }
        int maxButtons = mPreferences.getAppLauncherButtonCount();
        if (maxButtons <= 0) {
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            maxButtons = calculateSuggestionBarMaxButtons(displayMetrics);
        }
        mSuggestionBarView.setMaxButtonCount(maxButtons);
        mSuggestionBarView.setDefaultButtons(new ArrayList<>());
        mSuggestionBarView.setTextSize(10f);
        mSuggestionBarView.setSearchTolerance(mPreferences.getAppLauncherSearchTolerance());
        mSuggestionBarView.setBandW(mPreferences.isAppLauncherBwIconsEnabled());
        mSuggestionBarView.setIconScale(mPreferences.getAppLauncherIconScale());
        mSuggestionBarView.setAppBarOpacity(mPreferences.getAppBarOpacity());
        mSuggestionBarView.setBlurConfig(mPreferences.isExtraKeysBlurEnabled(), mPreferences.getExtraKeysBlurRadius());
        mSuggestionBarView.setInheritedTintColor(ContextCompat.getColor(this, R.color.background_accent));
        mSuggestionBarView.setAppDataProvider(mLauncherAppDataProvider);
        mSuggestionBarView.setConfigRepository(mLauncherConfigRepository);
        mSuggestionBarView.setAppCatalogChangedListener(this::syncAzScrubLettersAndTint);
        mSuggestionBarView.setOverflowInteractionListener(this::onSuggestionBarOverflowInteractionChanged);
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.onAnimationPreferenceUpdated();
        }
        mSuggestionBarView.reloadAllApps();
        syncAzScrubLettersAndTint();
    }

    private void onSuggestionBarOverflowInteractionChanged(boolean interacting) {
        mSuggestionBarInteractionActive = interacting;
        updateAzOverflowAffordance();
    }

    private void syncAzScrubLettersAndTint() {
        if (mAzScrubRowView == null || mSuggestionBarView == null) return;
        Set<Character> letters = new LinkedHashSet<>(mSuggestionBarView.getAvailableAzLetters());
        mAzScrubRowView.setVisibleLetters(letters);
        int base = resolveAzGestureAccentColor();
        int muted = mutedMonetShade(base);
        mAzScrubRowView.setTextColor(muted);
        mAzScrubRowView.setInteractionAccentColor(base);
        mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
        mAzScrubRowView.setLockedInlineLetter(null);
        int orbColor = brightMonetShade(base);
        int edgeColor = edgeMonetVariant(base);
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setColors(orbColor, edgeColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mLauncherAzGestureFxUnderlayView.setElevation(0f);
                mLauncherAzGestureFxUnderlayView.setTranslationZ(-dpToPx(8));
            }
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setColors(orbColor, edgeColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mLauncherAzGestureFxOverlayView.setElevation(dpToPx(30));
                mLauncherAzGestureFxOverlayView.setTranslationZ(dpToPx(30));
            }
        }
        updateAzOverflowAffordance();
        mAzScrubRowView.setBackgroundColor(Color.TRANSPARENT);
        mAzScrubRowView.bringToFront();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAzScrubRowView.setElevation(dpToPx(20));
            mAzScrubRowView.setTranslationZ(dpToPx(20));
        }
        if (mAzScrubRowView.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) mAzScrubRowView.getParent();
            parent.setClipChildren(false);
            parent.setClipToPadding(false);
            if (parent.getParent() instanceof ViewGroup) {
                ViewGroup grandParent = (ViewGroup) parent.getParent();
                grandParent.setClipChildren(false);
                grandParent.setClipToPadding(false);
            }
        }
    }

    private void handleAzGestureScrub(
        char letter,
        int selectionIndex,
        float touchX,
        float touchY,
        float rawX,
        float rawY,
        @NonNull AzScrubRowView.GesturePhase phase
    ) {
        if (mSuggestionBarView == null || mAzScrubRowView == null) {
            return;
        }

        if (phase == AzScrubRowView.GesturePhase.DOWN) {
            mAzGestureMode = AzGestureMode.AZ_TRACKING;
            mAzHasLockedSelection = false;
            mAzHasPreviewAnchor = false;
            mAzGestureDownTouchX = touchX;
            mAzGestureDownTouchY = touchY;
            mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
            mAzScrubRowView.setLockedInlineLetter(null);
        }

        mAzLastRawX = rawX;
        mAzLastRawY = rawY;
        int[] azLoc = new int[2];
        mAzScrubRowView.getLocationOnScreen(azLoc);
        mAzLastAnchorRawX = azLoc[0] + touchX;
        mAzLastAnchorRawY = azLoc[1] + (mAzScrubRowView.getHeight() * 0.5f);
        populateRawBounds(mAzScrubRowView, mAzRowRawBounds);
        populateRawBounds(mSuggestionBarView, mAppsRowRawBounds);
        populateRawBounds(findViewById(R.id.terminal_toolbar_view_pager), mExtraKeysRawBounds);

        if (letter == AzScrubRowView.PINNED_APPS_SYMBOL) {
            mSuggestionBarView.clearAzFocusedEntry();
            mSuggestionBarView.clearAzPreview();
            resetAzGestureState(false, false);
            updateAzOverflowAffordance();
            return;
        }

        mAzGestureActive = true;
        cancelAzOverflowRefresh();
        float rowHeight = Math.max(1f, mAzScrubRowView.getHeight());
        View extraKeysRow = findViewById(R.id.terminal_toolbar_view_pager);
        float extraKeysHeight = (extraKeysRow != null && extraKeysRow.getHeight() > 0)
            ? extraKeysRow.getHeight()
            : (rowHeight * 1.2f);
        float filterUpperBound = -(rowHeight * 0.10f);
        // Preserve forgiving AZ filtering in the region below the alphabet row,
        // through the extra-keys row, plus a small margin.
        float filterLowerBound = rowHeight + extraKeysHeight + (rowHeight * 0.25f);
        float unlockThreshold = rowHeight * AZ_RETURN_TOUCH_Y_RATIO;
        float unlockMaxBound = filterLowerBound + (rowHeight * 0.18f);
        float minUpwardTravel = Math.max(getResources().getDisplayMetrics().density * 10f, rowHeight * 0.22f);
        float dyFromDown = touchY - mAzGestureDownTouchY;
        float dxFromDown = touchX - mAzGestureDownTouchX;
        boolean upwardIntent = touchY <= (rowHeight * AZ_UPWARD_LOCK_TOUCH_Y_RATIO)
            && dyFromDown <= -minUpwardTravel
            && Math.abs(dyFromDown) >= (Math.abs(dxFromDown) * AZ_UPWARD_DIRECTION_RATIO);
        boolean withinAzFilterBand = touchY >= filterUpperBound && touchY <= filterLowerBound;
        boolean enteringUpwardLock = upwardIntent;
        boolean enteringIconTrack = isInAppsRowCorridor(rawY) || isInAzCaptureWedge(rawX, rawY);
        boolean returningToUpwardTrack = touchY >= unlockThreshold && touchY <= unlockMaxBound;
        boolean returningToIconTrack = !isInAppsRowCorridor(rawY) && !isInAzCaptureWedge(rawX, rawY) && isInAzReturnBand(rawY);

        if (mAzGestureMode == AzGestureMode.AZ_TRACKING) {
            if (enteringUpwardLock) {
                lockAzGestureAnchor(letter, selectionIndex, AzGestureMode.UPWARD_LOCKED);
            } else if (withinAzFilterBand || phase == AzScrubRowView.GesturePhase.DOWN) {
                persistAzPreviewAnchor(letter, selectionIndex);
            }
        } else if (mAzGestureMode == AzGestureMode.UPWARD_LOCKED && mAzHasLockedSelection) {
            if (returningToUpwardTrack && phase != AzScrubRowView.GesturePhase.UP) {
                mAzGestureMode = AzGestureMode.AZ_TRACKING;
                mAzHasLockedSelection = false;
                mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
                mAzScrubRowView.setLockedInlineLetter(null);
                mSuggestionBarView.clearAzFocusedEntry();
                if (withinAzFilterBand) {
                    persistAzPreviewAnchor(letter, selectionIndex);
                } else {
                    persistAzPreviewAnchor(mAzLockedLetter, mAzLockedSelectionIndex);
                }
            } else {
                if (mAzGestureMode == AzGestureMode.UPWARD_LOCKED && enteringIconTrack) {
                    mAzGestureMode = AzGestureMode.ICON_TRACKING_LOCKED;
                }
                persistAzPreviewAnchor(mAzLockedLetter, mAzLockedSelectionIndex);
                mAzScrubRowView.setLockedInlineLetter(Character.toUpperCase(mAzLockedLetter));
            }
        } else if (mAzGestureMode == AzGestureMode.ICON_TRACKING_LOCKED && mAzHasLockedSelection) {
            if (returningToIconTrack && phase != AzScrubRowView.GesturePhase.UP) {
                mAzGestureMode = AzGestureMode.AZ_TRACKING;
                mAzHasLockedSelection = false;
                mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
                mAzScrubRowView.setLockedInlineLetter(null);
                mSuggestionBarView.clearAzFocusedEntry();
                persistAzPreviewAnchor(mAzLockedLetter, mAzLockedSelectionIndex);
            } else {
                persistAzPreviewAnchor(mAzLockedLetter, mAzLockedSelectionIndex);
                mAzScrubRowView.setLockedInlineLetter(Character.toUpperCase(mAzLockedLetter));
            }
        }
        updateAzOverflowAffordance();

        SuggestionBarView.AzDragFocusResult focusResult = null;
        if (mAzGestureMode == AzGestureMode.ICON_TRACKING_LOCKED) {
            focusResult = mSuggestionBarView.resolveAzDragFocus(rawX, rawY);
            mAzCurrentFocusResult = focusResult;
        } else {
            mAzCurrentFocusResult = null;
        }

        char overlayLetter = (mAzGestureMode == AzGestureMode.UPWARD_LOCKED || mAzGestureMode == AzGestureMode.ICON_TRACKING_LOCKED) && mAzHasLockedSelection
            ? mAzLockedLetter
            : letter;
        updateAzOverlayState(focusResult, overlayLetter);
        updateAzEdgePagingLoop(focusResult);

        if (phase == AzScrubRowView.GesturePhase.UP) {
            boolean launched = false;
            if (mAzGestureMode == AzGestureMode.ICON_TRACKING_LOCKED && focusResult != null && focusResult.hasFocusEntry()) {
                if (mLauncherAzGestureFxOverlayView != null) {
                    mLauncherAzGestureFxOverlayView.playLaunchBloom(rawX, rawY);
                }
                launched = mSuggestionBarView.launchAzFocusedEntry(focusResult);
            }
            resetAzGestureState(!launched, false);
            updateAzOverflowAffordance();
            if (!launched) {
                scheduleAzOverflowRefresh();
            }
        }
    }

    private void persistAzPreviewAnchor(char letter, int selectionIndex) {
        if (mSuggestionBarView == null) return;
        mSuggestionBarView.persistAzPreview(letter, selectionIndex);
        mAzPreviewAnchorLetter = letter;
        mAzPreviewAnchorSelectionIndex = selectionIndex;
        mAzHasPreviewAnchor = true;
    }

    private void lockAzGestureAnchor(char fallbackLetter, int fallbackSelectionIndex, @NonNull AzGestureMode targetMode) {
        if (mAzHasPreviewAnchor) {
            mAzLockedLetter = mAzPreviewAnchorLetter;
            mAzLockedSelectionIndex = mAzPreviewAnchorSelectionIndex;
        } else {
            mAzLockedLetter = fallbackLetter;
            mAzLockedSelectionIndex = fallbackSelectionIndex;
        }
        persistAzPreviewAnchor(mAzLockedLetter, mAzLockedSelectionIndex);
        mAzGestureMode = targetMode;
        mAzHasLockedSelection = true;
        mAzLockedAnchorRawX = mAzLastAnchorRawX;
        mAzLockedAnchorRawY = mAzLastAnchorRawY;
        mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.INLINE_EMPHASIS_TRACK);
        mAzScrubRowView.setLockedInlineLetter(Character.toUpperCase(mAzLockedLetter));
    }

    private boolean isInAppsRowCorridor(float rawY) {
        if (mSuggestionBarView == null) {
            return false;
        }
        if (mAppsRowRawBounds.isEmpty()) {
            return false;
        }
        float tolerance = dpToPx(20);
        return rawY >= (mAppsRowRawBounds.top - tolerance) && rawY <= (mAppsRowRawBounds.bottom + tolerance);
    }

    private boolean isInAzCaptureWedge(float rawX, float rawY) {
        if (!mAzHasLockedSelection || mAppsRowRawBounds.isEmpty()) {
            return false;
        }
        float startY = mAzLockedAnchorRawY - dpToPx(4);
        float topLimit = mAppsRowRawBounds.top - dpToPx(20);
        float bottomLimit = mAppsRowRawBounds.bottom + dpToPx(20);
        if (rawY > startY || rawY < topLimit || rawY > bottomLimit) {
            return false;
        }
        float wedgeTravel = Math.max(dpToPx(24), startY - topLimit);
        float progress = Math.max(0f, Math.min(1f, (startY - rawY) / wedgeTravel));
        float targetHalfWidth = Math.max(dpToPx(28), mAppsRowRawBounds.width() * 0.14f);
        float halfWidth = dpToPx(10) + (targetHalfWidth * progress);
        return Math.abs(rawX - mAzLockedAnchorRawX) <= halfWidth;
    }

    private boolean isInAzReturnBand(float rawY) {
        if (mAzRowRawBounds.isEmpty()) {
            return false;
        }
        float top = mAzRowRawBounds.top - dpToPx(10);
        float bottom = mAzRowRawBounds.bottom + dpToPx(12);
        if (!mExtraKeysRawBounds.isEmpty()) {
            bottom = Math.max(bottom, mExtraKeysRawBounds.bottom + dpToPx(10));
        }
        return rawY >= top && rawY <= bottom;
    }

    private void updateAzOverlayState(@Nullable SuggestionBarView.AzDragFocusResult focusResult, char activeLetter) {
        if (mLauncherAzGestureFxUnderlayView == null && mLauncherAzGestureFxOverlayView == null) {
            return;
        }
        populateRawBounds(mAzScrubRowView, mAzRowRawBounds);
        populateRawBounds(mSuggestionBarView, mAppsRowRawBounds);
        populateRawBounds(findViewById(R.id.terminal_toolbar_view_pager), mExtraKeysRawBounds);
        applyAzFxRowBounds();
        LauncherAzGestureFxView.InteractionMode interactionMode =
            mAzGestureMode == AzGestureMode.ICON_TRACKING_LOCKED
                ? LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED
                : LauncherAzGestureFxView.InteractionMode.LETTER_TRACK;
        if (mSuggestionBarView != null) {
            if (interactionMode == LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED) {
                mSuggestionBarView.updateAzFocusedEntry(focusResult);
            } else {
                mSuggestionBarView.clearAzFocusedEntry();
            }
        }
        RectF focusBounds;
        if (interactionMode == LauncherAzGestureFxView.InteractionMode.LETTER_TRACK && mAzScrubRowView != null) {
            mAzLetterVisualMetrics.clear();
            boolean hasMetrics = mAzScrubRowView.getLetterVisualMetricsOnScreen(Character.toUpperCase(activeLetter), mAzLetterVisualMetrics);
            if (hasMetrics) {
                mAzFocusLetterRawBounds.set(mAzLetterVisualMetrics.glassBoundsRaw);
                focusBounds = mAzFocusLetterRawBounds;
            } else {
                mAzFocusLetterRawBounds.setEmpty();
                focusBounds = null;
            }
        } else {
            focusBounds = focusResult == null ? null : focusResult.iconBounds;
        }
        boolean overflowActive = mSuggestionBarView != null && mSuggestionBarView.hasAzOverflowPages();
        boolean canLeft = mSuggestionBarView != null && mSuggestionBarView.canAzPageLeft();
        boolean canRight = mSuggestionBarView != null && mSuggestionBarView.canAzPageRight();
        int currentPageIndex = mSuggestionBarView != null ? mSuggestionBarView.getAzCurrentPageIndex() : 0;
        int pageCount = mSuggestionBarView != null ? mSuggestionBarView.getAzVisiblePageCount() : 1;
        applyAzFxFilteredOverflowState(overflowActive, canLeft, canRight, currentPageIndex, pageCount);
        applyAzFxInteractionOverflowState(mAzGestureActive, canLeft, canRight, currentPageIndex, pageCount, true, false);

        float leftProximity = 0f;
        float rightProximity = 0f;
        if (mSuggestionBarView != null && interactionMode == LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED) {
            int[] loc = new int[2];
            mSuggestionBarView.getLocationOnScreen(loc);
            float localX = mAzLastRawX - loc[0];
            float width = Math.max(1f, mSuggestionBarView.getWidth());
            float edgeZone = Math.max(28f * getResources().getDisplayMetrics().density, width * 0.12f);
            if (canLeft && localX <= edgeZone) {
                leftProximity = Math.max(0f, 1f - (localX / Math.max(1f, edgeZone)));
            }
            if (canRight && localX >= width - edgeZone) {
                rightProximity = Math.max(0f, 1f - ((width - localX) / Math.max(1f, edgeZone)));
            }
        }
        applyAzFxEdgeProximity(leftProximity, rightProximity);
        applyAzFxDrag(
            mAzGestureActive,
            mAzLastRawX,
            mAzLastRawY,
            true,
            mAzLastAnchorRawX,
            mAzLastAnchorRawY,
            focusBounds,
            interactionMode
        );
    }

    private void populateRawBounds(@Nullable View view, @NonNull RectF out) {
        if (view == null || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) {
            out.setEmpty();
            return;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        out.set(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
    }

    private void updateAzOverflowAffordance() {
        if ((mLauncherAzGestureFxUnderlayView == null && mLauncherAzGestureFxOverlayView == null) || mSuggestionBarView == null) {
            return;
        }
        populateRawBounds(mAzScrubRowView, mAzRowRawBounds);
        populateRawBounds(mSuggestionBarView, mAppsRowRawBounds);
        populateRawBounds(findViewById(R.id.terminal_toolbar_view_pager), mExtraKeysRawBounds);
        applyAzFxRowBounds();
        boolean azOverflowActive = mSuggestionBarView.hasAzOverflowPages();
        applyAzFxFilteredOverflowState(
            azOverflowActive,
            mSuggestionBarView.canAzPageLeft(),
            mSuggestionBarView.canAzPageRight(),
            mSuggestionBarView.getAzCurrentPageIndex(),
            mSuggestionBarView.getAzVisiblePageCount()
        );
        boolean interactionActive = mAzGestureActive || mSuggestionBarInteractionActive;
        boolean canLeft = false;
        boolean canRight = false;
        int currentPageIndex = 0;
        int pageCount = 1;
        boolean showPageIndicators = azOverflowActive;
        boolean subtlePinnedIndicators = false;
        if (mAzGestureActive && azOverflowActive) {
            canLeft = mSuggestionBarView.canAzPageLeft();
            canRight = mSuggestionBarView.canAzPageRight();
            currentPageIndex = mSuggestionBarView.getAzCurrentPageIndex();
            pageCount = mSuggestionBarView.getAzVisiblePageCount();
        } else if (mSuggestionBarInteractionActive && mSuggestionBarView.hasPinnedOverflowPages()) {
            canLeft = mSuggestionBarView.canPinnedPageLeft();
            canRight = mSuggestionBarView.canPinnedPageRight();
            currentPageIndex = mSuggestionBarView.getPinnedCurrentPageIndex();
            pageCount = mSuggestionBarView.getPinnedVisiblePageCount();
        } else if (!mAzGestureActive && !mSuggestionBarInteractionActive && mSuggestionBarView.hasPinnedOverflowPages()) {
            canLeft = mSuggestionBarView.canPinnedPageLeft();
            canRight = mSuggestionBarView.canPinnedPageRight();
            currentPageIndex = mSuggestionBarView.getPinnedCurrentPageIndex();
            pageCount = mSuggestionBarView.getPinnedVisiblePageCount();
            subtlePinnedIndicators = true;
            interactionActive = true;
        }
        applyAzFxInteractionOverflowState(
            interactionActive,
            canLeft,
            canRight,
            currentPageIndex,
            pageCount,
            showPageIndicators,
            subtlePinnedIndicators
        );
    }

    private void applyAzFxRowBounds() {
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setRowBounds(mAzRowRawBounds, mAppsRowRawBounds, mExtraKeysRawBounds);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setRowBounds(mAzRowRawBounds, mAppsRowRawBounds, mExtraKeysRawBounds);
        }
    }

    private void applyAzFxFilteredOverflowState(boolean active, boolean canLeft, boolean canRight, int currentPageIndex, int pageCount) {
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setFilteredOverflowState(active, canLeft, canRight, currentPageIndex, pageCount);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setFilteredOverflowState(active, canLeft, canRight, currentPageIndex, pageCount);
        }
    }

    private void applyAzFxInteractionOverflowState(
        boolean active,
        boolean canLeft,
        boolean canRight,
        int currentPageIndex,
        int pageCount,
        boolean showPageIndicators,
        boolean subtlePinnedIndicators
    ) {
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setInteractionOverflowState(
                active, canLeft, canRight, currentPageIndex, pageCount, showPageIndicators, subtlePinnedIndicators
            );
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setInteractionOverflowState(
                active, canLeft, canRight, currentPageIndex, pageCount, showPageIndicators, subtlePinnedIndicators
            );
        }
    }

    private void applyAzFxEdgeProximity(float leftProximity, float rightProximity) {
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setEdgeProximity(leftProximity, rightProximity);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setEdgeProximity(leftProximity, rightProximity);
        }
    }

    private void applyAzFxDrag(boolean active, float rawX, float rawY, boolean anchorVisible, float anchorRawX, float anchorRawY, @Nullable RectF focusedBoundsRaw, @NonNull LauncherAzGestureFxView.InteractionMode mode) {
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.updateDrag(active, rawX, rawY, anchorVisible, anchorRawX, anchorRawY, focusedBoundsRaw, mode);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.updateDrag(active, rawX, rawY, anchorVisible, anchorRawX, anchorRawY, focusedBoundsRaw, mode);
        }
    }

    private void updateAzEdgePagingLoop(@Nullable SuggestionBarView.AzDragFocusResult focusResult) {
        stopAzEdgePagingLoop();
        if (!mAzGestureActive || mAzGestureMode != AzGestureMode.ICON_TRACKING_LOCKED || focusResult == null) {
            return;
        }
        if (focusResult.edge != SuggestionBarView.AZ_EDGE_LEFT && focusResult.edge != SuggestionBarView.AZ_EDGE_RIGHT) {
            return;
        }
        if (mSuggestionBarView == null) {
            return;
        }
        int pageDelta = focusResult.edge == SuggestionBarView.AZ_EDGE_LEFT ? -1 : 1;
        mAzEdgePagingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mAzGestureActive || mSuggestionBarView == null) {
                    return;
                }
                boolean changed = mSuggestionBarView.requestAzPageDelta(pageDelta, 640f);
                if (changed) {
                    updateAzOverflowAffordance();
                }
                SuggestionBarView.AzDragFocusResult fresh = mSuggestionBarView.resolveAzDragFocus(mAzLastRawX, mAzLastRawY);
                mAzCurrentFocusResult = fresh;
                updateAzOverlayState(fresh, mAzLockedLetter);
                if (mAzGestureActive && fresh.edge == focusResult.edge) {
                    mAzGestureHandler.postDelayed(this, AZ_EDGE_PAGE_REPEAT_INTERVAL_MS);
                }
            }
        };
        mAzGestureHandler.postDelayed(mAzEdgePagingRunnable, AZ_EDGE_PAGE_INITIAL_DELAY_MS);
    }

    private void stopAzEdgePagingLoop() {
        if (mAzEdgePagingRunnable != null) {
            mAzGestureHandler.removeCallbacks(mAzEdgePagingRunnable);
            mAzEdgePagingRunnable = null;
        }
    }

    private void scheduleAzOverflowRefresh() {
        cancelAzOverflowRefresh();
        mAzOverflowRefreshRunnable = this::updateAzOverflowAffordance;
        mAzGestureHandler.postDelayed(mAzOverflowRefreshRunnable, AZ_PREVIEW_TIMEOUT_REFRESH_MS);
    }

    private void cancelAzOverflowRefresh() {
        if (mAzOverflowRefreshRunnable != null) {
            mAzGestureHandler.removeCallbacks(mAzOverflowRefreshRunnable);
            mAzOverflowRefreshRunnable = null;
        }
    }

    private void resetAzGestureState(boolean keepOverflowAffordance, boolean clearPreview) {
        stopAzEdgePagingLoop();
        cancelAzOverflowRefresh();
        mAzGestureActive = false;
        mAzGestureMode = AzGestureMode.IDLE;
        mAzLockedLetter = '#';
        mAzLockedSelectionIndex = 0;
        mAzHasLockedSelection = false;
        mAzHasPreviewAnchor = false;
        mAzCurrentFocusResult = null;
        if (mAzScrubRowView != null) {
            mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
            mAzScrubRowView.setLockedInlineLetter(null);
        }
        if (mSuggestionBarView != null) {
            mSuggestionBarView.clearAzFocusedEntry();
        }
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.clearDrag(keepOverflowAffordance);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.clearDrag(keepOverflowAffordance);
        }
        if (clearPreview && mSuggestionBarView != null) {
            mSuggestionBarView.clearAzPreview();
        }
    }

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private int resolveAzGestureAccentColor() {
        int fallback = ContextCompat.getColor(this, R.color.main_accent);
        try {
            if (mPreferences != null
                && (mPreferences.isMonetBackgroundEnabled() || mPreferences.isMonetOverlayEnabled())
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return ContextCompat.getColor(this, android.R.color.system_accent1_500);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private int mutedMonetShade(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * 0.78f));
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * 0.68f));
        return Color.HSVToColor(0xE6, hsv);
    }

    private int brightMonetShade(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * 1.18f));
        hsv[2] = Math.max(0f, Math.min(1f, Math.max(hsv[2], 0.84f)));
        return Color.HSVToColor(0xF0, hsv);
    }

    private int edgeMonetVariant(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + 24f) % 360f;
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * 1.1f));
        hsv[2] = Math.max(0f, Math.min(1f, Math.max(hsv[2], 0.92f)));
        return Color.HSVToColor(0xE0, hsv);
    }

    private void lockScreenFromAzDoubleTap() {
        if (mPreferences == null || !mPreferences.isAppLauncherAzDoubleTapLockEnabled()) {
            return;
        }
        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        if (!manager.isPrivilegedAvailable()) {
            manager.requestPrivilegedPermission(ShizukuBackend.PERMISSION_REQUEST_CODE);
            return;
        }
        manager.executeCommand("input keyevent 223")
            .thenAccept(output -> {
                if (isSuccessfulPrivilegedCommandOutput(output)) {
                    return;
                }
                manager.executeCommand("input keyevent 26")
                    .thenAccept(fallback -> {
                        if (!isSuccessfulPrivilegedCommandOutput(fallback)) {
                            Logger.logWarn(LOG_TAG, "A-Z double tap lock failed: " + fallback);
                        }
                    })
                    .exceptionally(throwable -> {
                        Logger.logWarn(LOG_TAG, "A-Z double tap lock fallback failed: " + throwable.getMessage());
                        return null;
                    });
            })
            .exceptionally(throwable -> {
                Logger.logWarn(LOG_TAG, "A-Z double tap lock command failed: " + throwable.getMessage());
                return null;
            });
    }

    private boolean isSuccessfulPrivilegedCommandOutput(String output) {
        if (output == null) return false;
        String trimmed = output.trim();
        if (trimmed.isEmpty()) return true;
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("error")) return false;
        if (lower.contains("permission required")) return false;
        if (lower.contains("no privileged backend")) return false;
        return true;
    }

    private void applySuggestionBarInputChar() {
        if (mTerminalView == null || mPreferences == null)
            return;
        String inputChar = mPreferences.getAppLauncherInputChar();
        char splitChar = ' ';
        if (inputChar != null) {
            String trimmed = inputChar.trim();
            if (!trimmed.isEmpty()) {
                splitChar = trimmed.charAt(0);
            }
        }
        mTerminalView.setSplitChar(splitChar);
    }

    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);
        mTermuxTerminalViewClient.setSuggestionBarCallback(this);
        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);
        applySuggestionBarInputChar();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }

    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView, mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient, 0);
        mTermuxTerminalExtraKeys2 = new TermuxTerminalExtraKeys(this, mTerminalView, mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient, 1);
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar())
            terminalToolbarViewPager.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;
        updateAppLauncherBarHeight();
        setTerminalToolbarHeight();
        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);
        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void updateAppLauncherBarHeight() {
        if (mPreferences == null)
            return;
        int barHeightPx = Math.round(mTerminalToolbarDefaultHeight * mPreferences.getAppLauncherBarHeightScale());
        if (barHeightPx < 0) {
            barHeightPx = 0;
        }
        int azRowHeightPx = 0;
        boolean azEnabled = mPreferences.isAppLauncherAzRowEnabled();
        if (mPreferences.isAppLauncherAzRowEnabled()) {
            float density = getResources().getDisplayMetrics().density;
            float iconScale = mPreferences.getAppLauncherIconScale();
            float ratio = 0.40f + ((iconScale - 1f) * 0.08f);
            int target = Math.round(barHeightPx * ratio);
            int min = Math.round(16f * density);
            int max = Math.round(27f * density);
            azRowHeightPx = Math.max(min, Math.min(max, target));
        }
        updateViewHeight(R.id.apps_bar_viewpager, barHeightPx);
        updateViewHeight(R.id.apps_bar_az_row, azRowHeightPx);
        int interRowGapPx = AccessoryStackLayoutPolicy.computeAppsBarInterRowGapPx(
            azEnabled,
            getResources().getDisplayMetrics().density,
            mPreferences.getAppLauncherIconScale()
        );
        updateViewBottomMargin(R.id.apps_bar_viewpager, interRowGapPx);
    }

    public void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        View accessoryStackContainer = findViewById(R.id.accessory_stack_container);
        View appsBarViewPager = findViewById(R.id.apps_bar_viewpager);
        if (terminalToolbarViewPager == null || accessoryStackContainer == null)
            return;
        ViewGroup.LayoutParams toolbarLayoutParams = terminalToolbarViewPager.getLayoutParams();

        int i = terminalToolbarViewPager.getCurrentItem();
        int matrix = 0;
        if (i == 0) {
            if (mTermuxTerminalExtraKeys.getExtraKeysInfo() != null)
            matrix = mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length;
        } else {
            if (mTermuxTerminalExtraKeys2.getExtraKeysInfo() != null)
                matrix = mTermuxTerminalExtraKeys2.getExtraKeysInfo().getMatrix().length;
        }

        int toolbarHeightPx = Math.round(mTerminalToolbarDefaultHeight * matrix * mProperties.getTerminalToolbarHeightScaleFactor());
        toolbarLayoutParams.height = toolbarHeightPx;
        terminalToolbarViewPager.setLayoutParams(toolbarLayoutParams);

        int appsBarHeightPx = 0;
        int appsBarGapPx = 0;
        if (appsBarViewPager != null) {
            ViewGroup.LayoutParams appsBarLayoutParams = appsBarViewPager.getLayoutParams();
            if (appsBarLayoutParams != null) {
                appsBarHeightPx = appsBarLayoutParams.height;
                if (appsBarHeightPx < 0) {
                    appsBarHeightPx = 0;
                }
                if (appsBarLayoutParams instanceof ViewGroup.MarginLayoutParams) {
                    appsBarGapPx = Math.max(0, ((ViewGroup.MarginLayoutParams) appsBarLayoutParams).bottomMargin);
                }
            }
        }
        View azRow = findViewById(R.id.apps_bar_az_row);
        int azRowHeightPx = 0;
        if (azRow != null && azRow.getLayoutParams() != null) {
            azRowHeightPx = Math.max(0, azRow.getLayoutParams().height);
        }
        int combinedHeight = AccessoryStackLayoutPolicy.computeCombinedHeight(
            toolbarHeightPx,
            appsBarHeightPx,
            azRowHeightPx,
            appsBarGapPx
        );
        updateAccessoryStackContainerHeight(accessoryStackContainer, combinedHeight);
    }

    private void updateAccessoryStackContainerHeight(View view, int height) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams == null)
            return;
        if (layoutParams.height == height)
            return;
        layoutParams.height = height;
        view.setLayoutParams(layoutParams);
    }

    // Kept for test compatibility and to preserve existing RelativeLayout params in-place.
    private void updateExtraKeysBackgroundHeight(View view, int height) {
        updateAccessoryStackContainerHeight(view, height);
    }

    private void updateViewHeight(int viewId, int height) {
        View view = findViewById(viewId);
        if (view == null)
            return;
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams == null)
            return;
        if (layoutParams.height == height)
            return;
        layoutParams.height = height;
        view.setLayoutParams(layoutParams);
    }

    private void updateViewBottomMargin(int viewId, int marginBottom) {
        View view = findViewById(viewId);
        if (view == null) return;
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginParams.bottomMargin == marginBottom) return;
        marginParams.bottomMargin = marginBottom;
        view.setLayoutParams(marginParams);
    }

    public void toggleTerminalToolbar() {
        ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;
    
        boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar), true);
    
        updateViewVisibility(terminalToolbarViewPager, showNow);

        configureExtraKeysBackground();

        isToolbarHidden = !showNow;
    
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }
    
    private void updateViewVisibility(int viewId, boolean isVisible) {
        View view = findViewById(viewId);
        if (view != null) {
            updateViewVisibility(view, isVisible);
        }
    }
    
    private void updateViewVisibility(View view, boolean isVisible) {
        if (view != null) {
            int target = isVisible ? View.VISIBLE : View.GONE;
            if (view.getVisibility() != target) {
                view.setVisibility(target);
            }
        }
    }

    private void updateViewAlpha(View view, float alpha) {
        if (view == null) return;
        if (Math.abs(view.getAlpha() - alpha) > 0.001f) {
            view.setAlpha(alpha);
        }
    }
    

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null)
            return;
        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty())
                savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null, R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text), R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text), -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });
        findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }

    private void setBackgroundManager() {
        this.mTermuxBackgroundManager = new TermuxBackgroundManager(TermuxActivity.this);
    }

    private void setSystemWallpaperManager() {
        this.mTermuxSystemWallpaperManager = new TermuxSystemWallpaperManager(TermuxActivity.this);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else if (!getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().openDrawer(Gravity.LEFT);
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            if (mPreferences.isRemoveTaskOnActivityFinishEnabled())
                finishAndRemoveTask();
            else
                finish();
        }
    }

    /**
     * Show a toast and dismiss the last one if still visible.
     */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty())
            return;
        if (mLastToast != null)
            mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        SubMenu subMenu = menu.addSubMenu(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        subMenu.clearHeader();
        subMenu.add(SubMenu.NONE, CONTEXT_SUBMENU_FONT_AND_COLOR_ID, SubMenu.NONE, R.string.action_font_and_color);
        subMenu.add(SubMenu.NONE, CONTEXT_SUBMENU_SET_BACKROUND_IMAGE_ID, SubMenu.NONE, R.string.action_set_background_image);
        subMenu.add(SubMenu.NONE, CONTEXT_SUBMENU_REMOVE_BACKGROUND_IMAGE_ID, SubMenu.NONE, R.string.action_remove_background_image);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    /**
     * Hook system menu to show context menu instead.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();
        switch(item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_SUBMENU_FONT_AND_COLOR_ID:
                showFontAndColorDialog();
                return true;
            case CONTEXT_SUBMENU_SET_BACKROUND_IMAGE_ID:
                mTermuxBackgroundManager.setBackgroundImage();
                return true;
            case CONTEXT_SUBMENU_REMOVE_BACKGROUND_IMAGE_ID:
                mTermuxBackgroundManager.removeBackgroundImage(true);
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null)
            return;
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.ok, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.cancel, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);
            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showFontAndColorDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed)).setPositiveButton(R.string.action_styling_install, (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL)))).setNegativeButton(android.R.string.cancel, null).show();
        }
    }

    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }


    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {

            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;
                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, true, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG, getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));
                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG, getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: " + Arrays.toString(permissions) + ", grantResults: " + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public View getAccessoryStackContainerView() {
        return findViewById(R.id.accessory_stack_container);
    }

    public ExtraKeysView getExtraKeysView(int i) {
        if (i==0)
            return mExtraKeysView;
        else
            return mExtraKeysView2;
    }
    public ExtraKeysView getExtraKeysView() {
        int i = getTerminalToolbarViewPager().getCurrentItem();
        return getExtraKeysView(i);
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys(int i) {
        if (i==0)
            return mTermuxTerminalExtraKeys;
        else
            return mTermuxTerminalExtraKeys2;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView, int i) {
        if (i==0)
            mExtraKeysView = extraKeysView;
        else
            mExtraKeysView2 = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }

    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }

    public void termuxSessionListNotifyUpdated() {
        mTermuxSessionListViewController.notifyDataSetChanged();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }

    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }

    public TermuxBackgroundManager getmTermuxBackgroundManager() {
        return mTermuxBackgroundManager;
    }

    @Override
    public void reloadSuggestionBar(char inputChar) {
        if (mSuggestionBarView == null || mTerminalView == null) {
            return;
        }
        resetAzGestureState(false, true);
        mSuggestionBarView.onTerminalInteraction();
        String input = mTerminalView.getCurrentInput(inputChar);
        if (input == null) {
            input = "";
        }
        mSuggestionBarView.reloadWithInput(input, mTerminalView);
    }

    @Override
    public void reloadSuggestionBar(boolean delete, boolean enter) {
        if (mSuggestionBarView == null || mTerminalView == null) {
            return;
        }
        resetAzGestureState(false, true);
        mSuggestionBarView.onTerminalInteraction();
        if (enter) {
            mSuggestionBarView.reloadWithInput("", mTerminalView);
        } else {
            String input = mTerminalView.getCurrentInput();
            if (input == null) {
                input = "";
            }
            if (delete && input.length() > 0) {
                input = input.substring(0, input.length() - 1);
            }
            mSuggestionBarView.reloadWithInput(input, mTerminalView);
        }
    }

    private String normalizeSuggestionBarInput(String rawInput) {
        if (rawInput == null) {
            return "";
        }
        String trimmedRaw = rawInput.trim();
        if (trimmedRaw.isEmpty()) {
            return "";
        }
        if (trimmedRaw.indexOf(' ') >= 0) {
            return "";
        }
        if (containsAppSearchSeparator(trimmedRaw)) {
            return "";
        }
        if (trimmedRaw.length() > SUGGESTION_BAR_MAX_INPUT_CHARS) {
            return "";
        }
        return trimmedRaw;
    }

    private boolean containsAppSearchSeparator(String value) {
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case '/':
                case '.':
                case '-':
                case '_':
                case ':':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    public static void requestTermuxActivityStylingOnNextResume(Context context, boolean recreateActivity) {
        sPendingStyleReloadOnNextResume = true;
        updateTermuxActivityStyling(context, recreateActivity);
    }

    private static boolean consumePendingStyleReloadOnNextResume() {
        if (!sPendingStyleReloadOnNextResume) {
            return false;
        }
        sPendingStyleReloadOnNextResume = false;
        return true;
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        if (Build.VERSION.SDK_INT >= 28 ) {
            registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
        }
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void registerPackageChangeReceiver() {
        if (mPackageChangeReceiverRegistered)
            return;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        intentFilter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 28) {
            registerReceiver(mPackageChangeReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mPackageChangeReceiver, intentFilter);
        }
        mPackageChangeReceiverRegistered = true;
    }

    private void registerLauncherAppsCallback() {
        if (mLauncherAppsCallbackRegistered) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        try {
            if (mLauncherApps == null) {
                mLauncherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
            }
            if (mLauncherApps == null) {
                return;
            }
            if (mLauncherAppsCallback == null) {
                mLauncherAppsCallback = new LauncherApps.Callback() {
                    @Override
                    public void onPackageRemoved(String packageName, UserHandle user) {
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackageAdded(String packageName, UserHandle user) {
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackageChanged(String packageName, UserHandle user) {
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onShortcutsChanged(String packageName, List<ShortcutInfo> shortcuts, UserHandle user) {
                        // no-op: app list handled by package callbacks
                    }
                };
            }
            mLauncherApps.registerCallback(mLauncherAppsCallback, mAzGestureHandler);
            mLauncherAppsCallbackRegistered = true;
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "LauncherApps callback registration failed: " + throwable.getMessage());
        }
    }

    private void unregisterLauncherAppsCallback() {
        if (!mLauncherAppsCallbackRegistered || mLauncherApps == null || mLauncherAppsCallback == null) {
            return;
        }
        try {
            mLauncherApps.unregisterCallback(mLauncherAppsCallback);
        } catch (Throwable ignored) {
        }
        mLauncherAppsCallbackRegistered = false;
    }

    private void refreshSuggestionBarFromPackageState(boolean forceCatalogRefresh) {
        if (mSuggestionBarView == null) {
            return;
        }
        if (forceCatalogRefresh) {
            LauncherCtlApiServer.getInstance().invalidatePackageCaches();
            mSuggestionBarView.clearAppCache();
            mSuggestionBarView.reloadAllApps();
            syncAzScrubLettersAndTint();
        } else if (mSuggestionBarView.hasPinnedOverflowPages()) {
            // Keep affordance state fresh without forcing a catalog rebuild.
            updateAzOverflowAffordance();
        }
        String input = "";
        if (mTerminalView != null) {
            input = normalizeSuggestionBarInput(mTerminalView.getCurrentInput());
        }
        mSuggestionBarView.reloadWithInput(input, mTerminalView);
        updateAzOverflowAffordance();
    }

    private void scheduleSuggestionBarPackageRefresh(boolean immediate, boolean forceCatalogRefresh) {
        mPackageRefreshForceCatalogReload = mPackageRefreshForceCatalogReload || forceCatalogRefresh;
        mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
        if (immediate) {
            boolean forceNow = mPackageRefreshForceCatalogReload;
            mPackageRefreshForceCatalogReload = false;
            refreshSuggestionBarFromPackageState(forceNow);
            return;
        }
        mAzGestureHandler.postDelayed(mPackageRefreshRunnable, PACKAGE_REFRESH_DEBOUNCE_MS);
    }

    private void unregisterPackageChangeReceiver() {
        if (!mPackageChangeReceiverRegistered)
            return;
        try {
            unregisterReceiver(mPackageChangeReceiver);
        } catch (IllegalArgumentException ignored) {
            // Ignore if already unregistered.
        }
        mPackageChangeReceiverRegistered = false;
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null)
            return;
        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class PackageChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || mSuggestionBarView == null)
                return;
            String action = intent.getAction();
            if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
                Intent.ACTION_PACKAGE_REMOVED.equals(action) ||
                Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                scheduleSuggestionBarPackageRefresh(false, true);
            }
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null)
                return;
            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);
                switch(intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        sPendingStyleReloadOnNextResume = false;
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();
            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
               mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }
            if (mExtraKeysView2 != null) {
                mExtraKeysView2.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
               mExtraKeysView2.reload(mTermuxTerminalExtraKeys2.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }
            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }
        setMargins();
        updateAppLauncherBarHeight();
        applySuggestionBarPreferences();
        applySuggestionBarInputChar();
        setTerminalToolbarHeight();
        configureExtraKeysBackground();
        if (mPreferences != null) {
            if (mPreferences.isMonetBackgroundEnabled()) {
                applyTerminalMonetBackgroundOpacity();
            }
        }
        applyTerminalBlurBackground();
        applyTerminalGrainOverlay();
        applySeamlessStatusBackgroundModeIfNeeded();
        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();
        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
    }

    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
