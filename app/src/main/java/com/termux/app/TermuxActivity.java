package com.termux.app;

import android.annotation.SuppressLint;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.github.mmin18.widget.AndroidStockBlurImpl;
import com.github.mmin18.widget.RealtimeBlurView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.canhub.cropper.CropImage;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.launcher.animation.LauncherTransitionController;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.LockAccessibilityService;
import com.termux.launcherctl.LauncherCtlApiServer;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.ShizukuBackend;
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
import com.termux.app.activities.SettingsActivity;
import com.termux.app.theme.TermuxThemeManager;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
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
import com.termux.shared.theme.ThemeUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

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
    private boolean mSuggestionBarExplicitSearchActive;
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
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();
    private final BroadcastReceiver mPackageChangeReceiver = new PackageChangeReceiver();
    private boolean mPackageChangeReceiverRegistered = false;
    @Nullable private LauncherApps mLauncherApps;
    @Nullable private LauncherApps.Callback mLauncherAppsCallback;
    private boolean mLauncherAppsCallbackRegistered = false;
    private static final long PACKAGE_REFRESH_DEBOUNCE_MS = 120L;
    private static final long LAUNCHER_CATALOG_WARM_DELAY_MS = 450L;
    private boolean mPackageRefreshForceCatalogReload = false;
    private final Runnable mPackageRefreshRunnable = () -> {
        boolean forceCatalogRefresh = mPackageRefreshForceCatalogReload;
        mPackageRefreshForceCatalogReload = false;
        refreshSuggestionBarFromPackageState(forceCatalogRefresh);
    };
    private final Runnable mLauncherCatalogWarmRunnable = this::runLauncherCatalogWarmup;

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;
    @Nullable private AlertDialog mTerminalActionDialog;

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
    private boolean mLastLaunchWasLauncherEntry;

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
    private final RectF mIndicatorBandRawBounds = new RectF();
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

    private static final int CONTEXT_MENU_SET_WALLPAPER_ID = 2;

    private static final int CONTEXT_MENU_REMOVE_WALLPAPER_ID = 3;

    private static final int CONTEXT_MENU_LOOK_AND_FEEL_ID = 4;

    private static final int CONTEXT_MENU_APPS_BAR_ID = 5;

    private static final int CONTEXT_MENU_SETTINGS_ID = 6;

    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 7;

    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 8;

    private static final class TerminalActionItem {
        final int id;
        final CharSequence title;

        TerminalActionItem(int id, CharSequence title) {
            this.id = id;
            this.title = title;
        }

        @NonNull
        @Override
        public String toString() {
            return title.toString();
        }
    }

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";

    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";
    private static final int ACCESSORY_BLUR_DOWNSAMPLE_FACTOR = 4;
    private static final long ACCESSORY_BLUR_BACKSTOP_MS = 300_000L;
    private static volatile boolean sPendingStyleReloadOnNextResume = false;

    private static final int SUGGESTION_BAR_MIN_BUTTON_DP = 56;
    private static final int SUGGESTION_BAR_MAX_INPUT_CHARS = 10;
    private static final long EMPTY_SESSION_RECOVERY_DEBOUNCE_MS = 1500L;
    private static final long ACCESSORY_BLUR_RECOVERY_RETRY_MS = 120L;
    private static final boolean ACCESSORY_RENDER_TRACE = false;

    private boolean mSeamlessStatusBackgroundActive;
    private int mLastStatusBarInsetTop;
    private long mLastEmptySessionRecoveryElapsedMs;
    private boolean mEmptySessionRecoveryInProgress;
    private boolean mAccessoryRenderSyncPending;
    private boolean mLastImeVisible;
    @Nullable private String mPendingAccessoryRenderReason;
    @Nullable private ViewTreeObserver.OnGlobalLayoutListener mAccessoryKeyboardLayoutListener;
    @Nullable private View.OnLayoutChangeListener mAccessoryLayoutChangeListener;
    @Nullable private ActivityResultLauncher<PickVisualMediaRequest> mWallpaperPickerLauncher;
    @Nullable private ActivityResultLauncher<CropImageContractOptions> mWallpaperCropLauncher;
    private final int[] mTmpParentLocation = new int[2];
    private final int[] mTmpViewLocation = new int[2];
    private long mLastAccessoryRenderSyncUptimeMs;
    private long mLastAccessoryGeometryApplyUptimeMs;
    private long mDelayRootMarginAdjustmentsUntilUptimeMs;
    private boolean mImeTransitionInProgress;
    private boolean mFadeAccessoryBlurAfterImeRestore;
    private boolean mAccessoryBackdropDirty = true;
    private int mLastAccessoryBackdropBlurRadiusDp = -1;
    private boolean mLastAccessoryBackdropManagedSource;
    @NonNull private final Rect mLastAccessoryBackdropTargetRect = new Rect();
    @Nullable private Drawable mManagedWallpaperWindowBackground;
    private long mManagedWallpaperWindowBackgroundLastModified = -1L;
    private long mManagedWallpaperWindowBackgroundLength = -1L;
    @Nullable private Boolean mPendingImeGeometryVisible;
    private final Handler mAccessoryRenderHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDeferredImeGeometryRunnable = () -> {
        Boolean visible = mPendingImeGeometryVisible;
        mPendingImeGeometryVisible = null;
        if (visible == null) {
            return;
        }
        applyAccessoryGeometryIfNeeded(true, visible ? "ime:open:deferred" : "ime:close:deferred");
    };
    private final Runnable mAccessoryBlurHeartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsVisible) {
                return;
            }
            AccessoryRenderState state = buildAccessoryRenderState();
            if (!state.toolbarShown || !state.blurEnabled) {
                return;
            }
            if (!isAccessoryBlurHealthy(state)) {
                mAccessoryBackdropDirty = true;
                scheduleAccessoryRenderSync("blur:backstop");
            }
            mAccessoryRenderHandler.postDelayed(this, ACCESSORY_BLUR_BACKSTOP_MS);
        }
    };
    private final Runnable mAccessoryBlurRecoveryRunnable = () -> {
        if (!mIsVisible) {
            return;
        }
        AccessoryRenderState state = buildAccessoryRenderState();
        if (!state.toolbarShown || !state.blurEnabled) {
            return;
        }
        if (!isAccessoryBlurHealthy(state)) {
            mAccessoryBackdropDirty = true;
        }
        scheduleAccessoryRenderSync("blur:recovery");
    };
    private final Runnable mRestoreAccessoryBlurAfterImeRunnable = () -> {
        if (!mIsVisible) {
            return;
        }
        mImeTransitionInProgress = false;
        mAccessoryBackdropDirty = true;
        mFadeAccessoryBlurAfterImeRestore = shouldFadeAccessoryBlurAfterImeRestore();
        prepareAccessoryBlurRestoreFade();
        updateAccessoryRenderEffectBackdrop(buildAccessoryRenderState());
        startAccessoryBlurRestoreFade();
        mFadeAccessoryBlurAfterImeRestore = false;
        restartAccessoryBlurHeartbeat();
        scheduleAccessoryBlurRecovery();
    };
    private final WindowInsetsAnimationCompat.Callback mDockImeAnimationCallback =
        new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            @NonNull
            @Override
            public WindowInsetsAnimationCompat.BoundsCompat onStart(
                @NonNull WindowInsetsAnimationCompat animation,
                @NonNull WindowInsetsAnimationCompat.BoundsCompat bounds
            ) {
                if ((animation.getTypeMask() & Type.ime()) != 0) {
                    beginImeTransitionBlurPause();
                    mDelayRootMarginAdjustmentsUntilUptimeMs = SystemClock.uptimeMillis() + 180L;
                    applySmoothDockImeOffset(0);
                }
                return bounds;
            }

            @NonNull
            @Override
            public WindowInsetsCompat onProgress(
                @NonNull WindowInsetsCompat insets,
                @NonNull List<WindowInsetsAnimationCompat> runningAnimations
            ) {
                boolean imeAnimationRunning = false;
                for (WindowInsetsAnimationCompat animation : runningAnimations) {
                    if ((animation.getTypeMask() & Type.ime()) != 0) {
                        imeAnimationRunning = true;
                        break;
                    }
                }
                if (imeAnimationRunning) {
                    mDelayRootMarginAdjustmentsUntilUptimeMs = SystemClock.uptimeMillis() + 80L;
                    applySmoothDockImeOffset(0);
                }
                return insets;
            }

            @Override
            public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
                if ((animation.getTypeMask() & Type.ime()) != 0) {
                    mDelayRootMarginAdjustmentsUntilUptimeMs = SystemClock.uptimeMillis() + 40L;
                    applySmoothDockImeOffset(0);
                    mPendingImeGeometryVisible = null;
                    scheduleImeTransitionBlurRestore();
                }
            }
        };
    private final Runnable mAccessoryRenderSyncRunnable = () -> {
        mAccessoryRenderSyncPending = false;
        String reason = mPendingAccessoryRenderReason == null ? "unknown" : mPendingAccessoryRenderReason;
        mPendingAccessoryRenderReason = null;
        configureExtraKeysBackground();
        syncTerminalOverlayBottomInsetToAccessoryHeight();
        enforceAccessoryFxInvariants();
        logAccessoryRenderSnapshot(reason);
    };

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
            applyTerminalOverlayInsets(insetsCompat);
            return insetsCompat.toWindowInsets();
        });
        applySeamlessStatusBackgroundModeIfNeeded();
        ViewCompat.requestApplyInsets(content);
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer != null) {
            ViewCompat.setWindowInsetsAnimationCallback(accessoryContainer, mDockImeAnimationCallback);
        }
        if (mProperties.isUsingFullScreen()) {
            WindowInsetsController insetsController = getWindow().getInsetsController();
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.statusBars());
                insetsController.hide(WindowInsets.Type.navigationBars());
            }
        }
        // Must be done every time activity is created in order to registerForActivityResult,
        // Even if the logic of launching is based on user input.
        registerWallpaperActivityResultLaunchers();
        mLastLaunchWasLauncherEntry = isLauncherHomeIntent(getIntent());
        setTermuxTerminalViewAndClients();
        setTerminalToolbarView(savedInstanceState);
        setSettingsButtonView();
        setNewSessionButtonView();
        setToggleKeyboardView();
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
        if (isLauncherHomeIntent(intent)) {
            mLastLaunchWasLauncherEntry = true;
        }
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.maybeHandleGestureContract(intent, mSuggestionBarView);
        }
        if (isLauncherHomeIntent(intent)) {
            if (mSuggestionBarView != null) {
                mSuggestionBarView.resetTransientVisualState();
            }
            applyAccessoryGeometryIfNeeded(false, "onNewIntent:home");
            scheduleAccessoryRenderSync("onNewIntent:home");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Logger.logDebug(LOG_TAG, "onStart");
    
        if (mIsInvalidState) return;
    
        mIsVisible = true;
        if (mSuggestionBarView != null) {
            mSuggestionBarView.setHostVisible(true);
            scheduleLauncherCatalogWarmup();
        }
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
        updateWindowBackgroundForCurrentSession();
    
        if (mPreferences.isTerminalMarginAdjustmentEnabled()) {
            addTermuxActivityRootViewGlobalLayoutListener();
        }
        addAccessoryKeyboardLayoutListener();
        addAccessoryLayoutChangeListeners();

        syncTerminalWallpaperRenderingMode();
        applySeamlessStatusBackgroundModeIfNeeded();
        applyTerminalSurfaceAppearance();
        syncRecentsVisibilityPolicy();
        configureBackgroundBlur(R.id.sessions_backgroundblur, R.id.sessions_background, false, mPreferences.getSessionsOpacity() / 100f, 0);
        restartAccessoryBlurHeartbeat();
        scheduleAccessoryBlurRecovery();
        registerTermuxActivityBroadcastReceiver();
        registerPackageChangeReceiver();
        registerLauncherAppsCallback();
        getWindow().getDecorView().post(() -> LauncherCtlApiServer.getInstance().ensureStartedAsync(getApplicationContext()));
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

        updateWindowBackgroundForCurrentSession();
        syncTerminalWallpaperRenderingMode();
        applySeamlessStatusBackgroundModeIfNeeded();
        applyTerminalSurfaceAppearance();
        syncRecentsVisibilityPolicy();
        applyWallpaperOffsetFixIfNeeded();
        configureBackgroundBlur(R.id.sessions_backgroundblur, R.id.sessions_background, false, mPreferences.getSessionsOpacity() / 100f, 0);
        scheduleAccessoryRenderSync("wallpaper:resume");
        restartAccessoryBlurHeartbeat();
        scheduleAccessoryBlurRecovery();
        refreshShizukuLockBackendIfNeeded();
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

    private void applyTerminalSurfaceAppearance() {
        if (mPreferences == null) {
            return;
        }
        View terminalSurfaceHost = findViewById(R.id.terminal_surface_host);
        View terminalBodySurface = findViewById(R.id.terminal_background);
        View terminalStatusSurface = findViewById(R.id.terminal_status_bar_background);
        View terminalView = findViewById(R.id.terminal_view);
        if (terminalSurfaceHost == null || terminalBodySurface == null || terminalStatusSurface == null) {
            return;
        }
        boolean wallpaperMode = shouldUseWallpaperPassthroughMode();
        int accessoryBaseColor = resolveAccessoryGlassBaseColor();
        int sessionsBaseColor = resolveAccessoryGlassBaseColor();
        applyGlassSurfaceColor(R.id.extrakeys_background, accessoryBaseColor);
        applyGlassSurfaceColor(R.id.activity_termux_bottom_space_background, accessoryBaseColor);
        applyGlassSurfaceColor(R.id.sessions_background, sessionsBaseColor);

        if (wallpaperMode) {
            boolean showSurface = shouldShowTerminalOverlaySurface();
            int terminalSurfaceColor = showSurface ? resolveTerminalSurfaceColor() : Color.TRANSPARENT;
            terminalSurfaceHost.setBackgroundColor(Color.TRANSPARENT);
            terminalBodySurface.setBackgroundColor(terminalSurfaceColor);
            terminalBodySurface.setVisibility(showSurface && Color.alpha(terminalSurfaceColor) > 0 ? View.VISIBLE : View.GONE);
            terminalStatusSurface.setBackgroundColor(Color.TRANSPARENT);
            terminalStatusSurface.setVisibility(View.GONE);
            if (terminalView != null) {
                terminalView.setBackgroundColor(Color.TRANSPARENT);
                if (terminalView instanceof TerminalView) {
                    ((TerminalView) terminalView).setTransparentFrameOverlayColor(Color.TRANSPARENT);
                }
            }
            applyTerminalStatusBarSurfaceColor(showSurface, terminalSurfaceColor);
            return;
        }

        boolean showSurface = true;
        int terminalSurfaceColor = resolveTerminalSurfaceColor();
        terminalSurfaceHost.setBackgroundColor(Color.TRANSPARENT);
        terminalBodySurface.setBackgroundColor(terminalSurfaceColor);
        terminalBodySurface.setVisibility(showSurface && Color.alpha(terminalSurfaceColor) > 0 ? View.VISIBLE : View.GONE);
        terminalStatusSurface.setBackgroundColor(terminalSurfaceColor);
        terminalStatusSurface.setVisibility(shouldShowTerminalStatusBarSurface(showSurface, terminalSurfaceColor) ? View.VISIBLE : View.GONE);
        if (terminalView != null) {
            terminalView.setBackgroundColor(Color.TRANSPARENT);
            if (terminalView instanceof TerminalView) {
                ((TerminalView) terminalView).setTransparentFrameOverlayColor(Color.TRANSPARENT);
            }
        }
        applyTerminalStatusBarSurfaceColor(showSurface, terminalSurfaceColor);
    }

    private void applyTerminalStatusBarSurfaceColor(boolean showSurface, int terminalSurfaceColor) {
        int targetColor = shouldEnableSeamlessStatusBackground() ? terminalSurfaceColor
            : (!shouldUseWallpaperPassthroughMode()
                ? getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base)
                : Color.TRANSPARENT);
        if (getWindow() != null) {
            getWindow().setStatusBarColor(targetColor);
        }
    }

    private void applyWallpaperOffsetFixIfNeeded() {
        if (!shouldUseWallpaperPassthroughMode()) {
            return;
        }
        if (getWindow() == null || getWindow().getDecorView() == null) {
            return;
        }
        IBinder windowToken = getWindow().getDecorView().getWindowToken();
        if (windowToken == null) {
            return;
        }
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
            Rect frameRect = getSystemWallpaperFrameRect();
            wallpaperManager.suggestDesiredDimensions(frameRect.width(), frameRect.height());
            wallpaperManager.setWallpaperOffsetSteps(1f, 1f);
            wallpaperManager.setWallpaperOffsets(windowToken, 0.5f, 0.5f);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply wallpaper offset fix", e);
        }
    }

    private int resolveAccessoryGlassBaseColor() {
        if (isNightThemeActive()) {
            return getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        }
        return getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh, R.color.termux_surface_panel_high);
    }

    private int resolveAccessoryOutlineColor() {
        return getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOutlineVariant, R.color.termux_outline_variant);
    }

    private void applyGlassSurfaceColor(int viewId, int surfaceColor) {
        View surface = findViewById(viewId);
        if (surface != null) {
            surface.setBackgroundColor(surfaceColor);
        }
    }

    private float resolveOpacityAlpha(int opacityPercent) {
        int clamped = Math.max(0, Math.min(100, opacityPercent));
        return clamped / 100f;
    }

    private int resolveTerminalOverlayBaseColor() {
        if (isNightThemeActive()) {
            return getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        }
        return Color.parseColor("#1C1B1F");
    }

    private int resolveTerminalSurfaceColor() {
        int baseColor = shouldUseWallpaperPassthroughMode()
            ? resolveTerminalOverlayBaseColor()
            : getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        int alpha = Math.round(resolveOpacityAlpha(
            mPreferences != null ? mPreferences.getTerminalBackgroundOpacity() : 100
        ) * 255f);
        return (alpha << 24) | (baseColor & 0x00FFFFFF);
    }

    private int resolveAccessorySurfaceColor(float surfaceAlpha) {
        int baseColor = shouldUseWallpaperPassthroughMode()
            ? resolveAccessoryGlassBaseColor()
            : getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        int alpha = Math.round(Math.max(0f, Math.min(1f, surfaceAlpha)) * 255f);
        return (alpha << 24) | (baseColor & 0x00FFFFFF);
    }

    private int getTermuxThemeColor(int attr, int fallbackRes) {
        return ThemeUtils.getSystemAttrColor(this, attr, ContextCompat.getColor(this, fallbackRes));
    }

    private boolean isNightThemeActive() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int withAlphaComponent(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private boolean shouldShowTerminalOverlaySurface() {
        if (mProperties == null || mProperties.isUsingFullScreen()) {
            return false;
        }
        if (mPreferences == null) {
            return false;
        }
        if (!shouldUseWallpaperPassthroughMode()) {
            return true;
        }
        return mPreferences.getTerminalBackgroundOpacity() > 0;
    }

    @Nullable
    private Rect resolveAccessoryContentBounds() {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer == null || accessoryContainer.getWidth() <= 0 || accessoryContainer.getHeight() <= 0) {
            return null;
        }
        Rect containerRect = new Rect();
        if (!accessoryContainer.getGlobalVisibleRect(containerRect)) {
            return null;
        }
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        int[] candidateIds = {
            R.id.apps_bar_viewpager,
            R.id.apps_bar_az_row,
            R.id.terminal_toolbar_view_pager
        };
        for (int candidateId : candidateIds) {
            View candidate = findViewById(candidateId);
            if (candidate == null || candidate.getVisibility() != View.VISIBLE || candidate.getHeight() <= 0) {
                continue;
            }
            Rect candidateRect = new Rect();
            if (!candidate.getGlobalVisibleRect(candidateRect)) {
                continue;
            }
            candidateRect.offset(-containerRect.left, -containerRect.top);
            top = Math.min(top, candidateRect.top);
            bottom = Math.max(bottom, candidateRect.bottom);
        }
        if (top == Integer.MAX_VALUE || bottom <= top) {
            return null;
        }
        return new Rect(
            0,
            Math.max(0, top),
            accessoryContainer.getWidth(),
            Math.min(accessoryContainer.getHeight(), bottom)
        );
    }

    private void applyAccessoryLayerBounds(int viewId, @Nullable Rect bounds) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layoutParams;
        int targetTop = 0;
        int targetWidth = ViewGroup.LayoutParams.MATCH_PARENT;
        int targetHeight = ViewGroup.LayoutParams.MATCH_PARENT;
        ViewParent parent = view.getParent();
        if (parent instanceof View) {
            View parentView = (View) parent;
            if (parentView.getWidth() > 0) {
                targetWidth = parentView.getWidth();
            }
            if (parentView.getHeight() > 0) {
                targetHeight = parentView.getHeight();
            }
        }
        if (params.leftMargin != 0 || params.topMargin != targetTop ||
            params.rightMargin != 0 || params.width != targetWidth || params.height != targetHeight) {
            params.leftMargin = 0;
            params.topMargin = targetTop;
            params.rightMargin = 0;
            params.width = targetWidth;
            params.height = targetHeight;
            view.setLayoutParams(params);
        }
    }

    private void configureAccessoryTopEdgeFx(boolean visible, float barAlpha) {
        View edgeFx = findViewById(R.id.accessory_top_edge_fx);
        if (edgeFx == null) {
            return;
        }
        if (!visible) {
            edgeFx.setVisibility(View.GONE);
            edgeFx.setBackground(null);
            return;
        }

        int highlight = withAlphaComponent(Color.WHITE, Math.round(30f * Math.max(0.35f, barAlpha)));
        int shadow = withAlphaComponent(resolveAccessoryOutlineColor(), Math.round(26f * Math.max(0.40f, barAlpha)));
        GradientDrawable edge = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { highlight, shadow, Color.TRANSPARENT }
        );
        edgeFx.setBackground(edge);
        edgeFx.setVisibility(View.VISIBLE);
    }

    private void configureBackgroundBlur(int blurViewId, int backgroundViewId, boolean isBlurEnabled, float surfaceAlpha, int blurRadiusDp) {
        View blurView = findViewById(blurViewId);
        View backgroundView = findViewById(backgroundViewId);
        applyRealtimeBlurRadius(blurView, blurRadiusDp);
        blurView.setVisibility(isBlurEnabled ? View.VISIBLE : View.GONE);
        backgroundView.setAlpha(surfaceAlpha);
    }

    private static final class AccessoryRenderState {
        final boolean toolbarShown;
        final boolean blurEnabled;
        final boolean appsRowEnabled;
        final boolean azRowEnabled;
        final float barAlpha;
        final int blurRadiusDp;

        AccessoryRenderState(boolean toolbarShown, boolean blurEnabled, boolean appsRowEnabled, boolean azRowEnabled, float barAlpha, int blurRadiusDp) {
            this.toolbarShown = toolbarShown;
            this.blurEnabled = blurEnabled;
            this.appsRowEnabled = appsRowEnabled;
            this.azRowEnabled = azRowEnabled;
            this.barAlpha = barAlpha;
            this.blurRadiusDp = blurRadiusDp;
        }
    }

    private static final class DockLayoutMetrics {
        final int appsBarHeightPx;
        final int indicatorBandHeightPx;
        final int azRowHeightPx;
        final int interRowGapPx;

        DockLayoutMetrics(int appsBarHeightPx, int indicatorBandHeightPx, int azRowHeightPx, int interRowGapPx) {
            this.appsBarHeightPx = Math.max(0, appsBarHeightPx);
            this.indicatorBandHeightPx = Math.max(0, indicatorBandHeightPx);
            this.azRowHeightPx = Math.max(0, azRowHeightPx);
            this.interRowGapPx = Math.max(0, interRowGapPx);
        }

        int combinedHeight(int toolbarHeightPx) {
            return AccessoryStackLayoutPolicy.computeCombinedHeight(
                toolbarHeightPx,
                appsBarHeightPx,
                azRowHeightPx,
                indicatorBandHeightPx
            );
        }
    }

    @NonNull
    private AccessoryRenderState buildAccessoryRenderState() {
        if (mPreferences == null) {
            return new AccessoryRenderState(false, false, false, false, 1.0f, 0);
        }
        boolean appsRowEnabled = mPreferences.isAppLauncherAppsRowEnabled();
        int blurRadiusDp = getEffectiveExtraKeysBlurRadius();
        return new AccessoryRenderState(
            mPreferences.shouldShowTerminalToolbar(),
            blurRadiusDp > 0,
            appsRowEnabled,
            appsRowEnabled && mPreferences.isAppLauncherAzRowEnabled(),
            mPreferences.getAppBarOpacity() / 100f,
            blurRadiusDp
        );
    }

    private int getEffectiveExtraKeysBlurRadius() {
        if (mPreferences == null) {
            return 0;
        }
        int blurRadiusDp = mPreferences.getExtraKeysBlurRadius();
        if (blurRadiusDp <= 0 || isLiveWallpaperActive()) {
            return 0;
        }
        return blurRadiusDp;
    }

    private boolean isLiveWallpaperActive() {
        try {
            WallpaperInfo wallpaperInfo = WallpaperManager.getInstance(this).getWallpaperInfo();
            return wallpaperInfo != null;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to detect live wallpaper state", e);
            return false;
        }
    }

    private void configureExtraKeysBackground() {
        applyAccessoryRenderState(buildAccessoryRenderState());
    }

    private boolean shouldUseAccessoryRenderEffectBlur(@NonNull AccessoryRenderState state) {
        return state.toolbarShown
            && state.blurEnabled;
    }

    private void clearAccessoryRenderEffectBackdrop() {
        ImageView backdrop = findViewById(R.id.accessory_blur_backdrop);
        if (backdrop == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdrop.setRenderEffect(null);
        }
        backdrop.setImageDrawable(null);
        backdrop.setVisibility(View.GONE);
        mAccessoryBackdropDirty = true;
        mLastAccessoryBackdropBlurRadiusDp = -1;
        mLastAccessoryBackdropManagedSource = false;
        mLastAccessoryBackdropTargetRect.setEmpty();
    }

    private void restartAccessoryBlurHeartbeat() {
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurHeartbeatRunnable);
        AccessoryRenderState state = buildAccessoryRenderState();
        if (mIsVisible && state.toolbarShown && state.blurEnabled) {
            mAccessoryRenderHandler.postDelayed(mAccessoryBlurHeartbeatRunnable, ACCESSORY_BLUR_BACKSTOP_MS);
        }
    }

    private void scheduleAccessoryBlurRecovery() {
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurRecoveryRunnable);
        AccessoryRenderState state = buildAccessoryRenderState();
        if (mIsVisible && state.toolbarShown && state.blurEnabled) {
            mAccessoryRenderHandler.postDelayed(mAccessoryBlurRecoveryRunnable, ACCESSORY_BLUR_RECOVERY_RETRY_MS);
        }
    }

    private void beginImeTransitionBlurPause() {
        mAccessoryRenderHandler.removeCallbacks(mRestoreAccessoryBlurAfterImeRunnable);
        if (mImeTransitionInProgress) {
            return;
        }
        mImeTransitionInProgress = true;
        setAccessoryBlurLayerAlpha(1f);
    }

    private void scheduleImeTransitionBlurRestore() {
        mAccessoryRenderHandler.removeCallbacks(mRestoreAccessoryBlurAfterImeRunnable);
        mAccessoryRenderHandler.postDelayed(mRestoreAccessoryBlurAfterImeRunnable, 90L);
    }

    private void prepareAccessoryBlurRestoreFade() {
        AccessoryRenderState state = buildAccessoryRenderState();
        if (!state.toolbarShown || !state.blurEnabled) {
            setAccessoryBlurLayerAlpha(1f);
            return;
        }
        setAccessoryBlurLayerAlpha(mFadeAccessoryBlurAfterImeRestore ? 0f : 1f);
    }

    private void startAccessoryBlurRestoreFade() {
        AccessoryRenderState state = buildAccessoryRenderState();
        if (!state.toolbarShown || !state.blurEnabled || !mFadeAccessoryBlurAfterImeRestore) {
            setAccessoryBlurLayerAlpha(1f);
            return;
        }
        animateAccessoryBlurLayer(findViewById(R.id.extrakeys_backgroundblur));
        animateAccessoryBlurLayer(findViewById(R.id.accessory_blur_backdrop));
    }

    private boolean shouldFadeAccessoryBlurAfterImeRestore() {
        ImageView backdrop = findViewById(R.id.accessory_blur_backdrop);
        return backdrop == null
            || backdrop.getVisibility() != View.VISIBLE
            || backdrop.getDrawable() == null
            || backdrop.getAlpha() <= 0f;
    }

    private void setAccessoryBlurLayerAlpha(float alpha) {
        setAccessoryBlurLayerAlpha(findViewById(R.id.extrakeys_backgroundblur), alpha);
        setAccessoryBlurLayerAlpha(findViewById(R.id.accessory_blur_backdrop), alpha);
    }

    private void setAccessoryBlurLayerAlpha(@Nullable View view, float alpha) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setAlpha(alpha);
    }

    private void animateAccessoryBlurLayer(@Nullable View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return;
        }
        view.animate()
            .alpha(1f)
            .setDuration(140L)
            .withEndAction(() -> view.setAlpha(1f))
            .start();
    }

    private boolean isAccessoryBlurHealthy(@NonNull AccessoryRenderState state) {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer == null || accessoryContainer.getVisibility() != View.VISIBLE) {
            return !state.toolbarShown;
        }
        boolean useRenderEffectBlur = shouldUseAccessoryRenderEffectBlur(state);
        if (useRenderEffectBlur) {
            ImageView backdrop = findViewById(R.id.accessory_blur_backdrop);
            return backdrop != null
                && backdrop.getVisibility() == View.VISIBLE
                && backdrop.getDrawable() != null;
        }
        View realtimeBlur = findViewById(R.id.extrakeys_backgroundblur);
        return realtimeBlur != null
            && realtimeBlur.getVisibility() == View.VISIBLE
            && realtimeBlur instanceof RealtimeBlurView;
    }

    private boolean shouldUseManagedWallpaperBlurSource() {
        if (mPreferences == null) {
            return false;
        }
        int storedWallpaperId = mPreferences.getManagedWallpaperSystemId();
        if (storedWallpaperId <= 0 || storedWallpaperId != getCurrentSystemWallpaperId()) {
            return false;
        }
        return getManagedWallpaperExactFile().isFile();
    }

    private int computeAccessoryBackdropHorizontalOverscanPx(int blurRadiusDp) {
        float blurRadiusPx = ViewUtils.dpToPx(this, Math.max(0, blurRadiusDp));
        float density = getResources().getDisplayMetrics().density;
        return Math.max(0, Math.round((blurRadiusPx * 2f) + (density * 2f)));
    }

    private void applyAccessoryBackdropOverscan(@NonNull ImageView backdrop, @NonNull View surfaceHost, int horizontalOverscanPx) {
        ViewGroup.LayoutParams layoutParams = backdrop.getLayoutParams();
        if (!(layoutParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layoutParams;
        int targetWidth = Math.max(1, surfaceHost.getWidth() + (horizontalOverscanPx * 2));
        int targetHeight = Math.max(1, surfaceHost.getHeight());
        int targetLeftMargin = -horizontalOverscanPx;
        int targetTopMargin = 0;
        if (params.width != targetWidth || params.height != targetHeight ||
            params.leftMargin != targetLeftMargin || params.topMargin != targetTopMargin) {
            params.width = targetWidth;
            params.height = targetHeight;
            params.leftMargin = targetLeftMargin;
            params.topMargin = targetTopMargin;
            params.rightMargin = 0;
            params.bottomMargin = 0;
            params.gravity = Gravity.TOP | Gravity.START;
            backdrop.setLayoutParams(params);
        }
    }

    @NonNull
    private Rect buildAccessoryBackdropTargetRect(@NonNull View surfaceHost, int horizontalOverscanPx) {
        surfaceHost.getLocationOnScreen(mTmpViewLocation);
        return new Rect(
            mTmpViewLocation[0] - horizontalOverscanPx,
            mTmpViewLocation[1],
            mTmpViewLocation[0] + Math.max(1, surfaceHost.getWidth()) + horizontalOverscanPx,
            mTmpViewLocation[1] + Math.max(1, surfaceHost.getHeight())
        );
    }

    @Nullable
    private Bitmap createManagedWallpaperBackdropBitmapForRect(@NonNull Rect targetRect, @NonNull View wallpaperFrame) {
        File sourceFile = getManagedWallpaperExactFile();
        Bitmap sourceBitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
        if (sourceBitmap == null) {
            return null;
        }

        try {
            Rect frameRect = getManagedWallpaperFrameRect();
            int targetWidth = Math.max(1, targetRect.width());
            int targetHeight = Math.max(1, targetRect.height());

            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            int frameWidth = Math.max(1, frameRect.width());
            int frameHeight = Math.max(1, frameRect.height());
            int sourceWidth = Math.max(1, sourceBitmap.getWidth());
            int sourceHeight = Math.max(1, sourceBitmap.getHeight());
            float scale = Math.max((float) frameWidth / sourceWidth, (float) frameHeight / sourceHeight);
            float drawWidth = sourceWidth * scale;
            float drawHeight = sourceHeight * scale;
            float translateX = frameRect.left + ((frameWidth - drawWidth) / 2f) - targetRect.left;
            float translateY = frameRect.top + ((frameHeight - drawHeight) / 2f) - targetRect.top;

            Matrix shaderMatrix = new Matrix();
            shaderMatrix.setScale(scale, scale);
            shaderMatrix.postTranslate(translateX, translateY);

            BitmapShader shader = new BitmapShader(sourceBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shader.setLocalMatrix(shaderMatrix);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setShader(shader);
            canvas.drawRect(0f, 0f, targetWidth, targetHeight, paint);
            return bitmap;
        } finally {
            sourceBitmap.recycle();
        }
    }

    @Nullable
    private Bitmap createWallpaperBackdropBitmapForRect(@NonNull Rect targetRect, @NonNull View wallpaperFrame) {
        if (shouldUseManagedWallpaperBlurSource()) {
            Bitmap managedBackdrop = createManagedWallpaperBackdropBitmapForRect(targetRect, wallpaperFrame);
            if (managedBackdrop != null) {
                return managedBackdrop;
            }
        }

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        Drawable wallpaper = wallpaperManager.getDrawable();
        if (wallpaper == null) {
            return null;
        }

        int targetWidth = Math.max(1, targetRect.width());
        int targetHeight = Math.max(1, targetRect.height());
        Rect frameRect = getSystemWallpaperFrameRect();
        int frameWidth = Math.max(1, frameRect.width());
        int frameHeight = Math.max(1, frameRect.height());

        int intrinsicWidth = wallpaper.getIntrinsicWidth() > 0 ? wallpaper.getIntrinsicWidth() : frameWidth;
        int intrinsicHeight = wallpaper.getIntrinsicHeight() > 0 ? wallpaper.getIntrinsicHeight() : frameHeight;
        float scale = Math.max((float) frameWidth / intrinsicWidth, (float) frameHeight / intrinsicHeight);
        int drawWidth = Math.max(frameWidth, Math.round(intrinsicWidth * scale));
        int drawHeight = Math.max(frameHeight, Math.round(intrinsicHeight * scale));

        int frameScreenX = frameRect.left;
        int frameScreenY = frameRect.top;
        int offsetLeft = frameScreenX + Math.round((frameWidth - drawWidth) / 2f);
        int offsetTop = frameScreenY + Math.round((frameHeight - drawHeight) / 2f);

        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable drawable = wallpaper.getConstantState() != null
            ? wallpaper.getConstantState().newDrawable().mutate()
            : wallpaper.mutate();
        drawable.setBounds(
            offsetLeft - targetRect.left,
            offsetTop - targetRect.top,
            offsetLeft - targetRect.left + drawWidth,
            offsetTop - targetRect.top + drawHeight
        );
        drawable.draw(canvas);
        return bitmap;
    }

    @Nullable
    private Bitmap createPreBlurredWallpaperBackdropBitmap(@NonNull Bitmap sourceBitmap, int blurRadiusDp) {
        float blurRadiusPx = ViewUtils.dpToPx(this, Math.max(0, blurRadiusDp));
        if (blurRadiusPx <= 0f) {
            return sourceBitmap;
        }

        float downsampleFactor = ACCESSORY_BLUR_DOWNSAMPLE_FACTOR;
        float scriptRadius = blurRadiusPx / downsampleFactor;
        if (scriptRadius > 25f) {
            downsampleFactor = (float) Math.ceil(blurRadiusPx / 25f);
            scriptRadius = blurRadiusPx / downsampleFactor;
        }
        scriptRadius = Math.max(0.1f, Math.min(25f, scriptRadius));

        int scaledWidth = Math.max(1, Math.round(sourceBitmap.getWidth() / downsampleFactor));
        int scaledHeight = Math.max(1, Math.round(sourceBitmap.getHeight() / downsampleFactor));
        Bitmap blurInput = null;
        Bitmap blurOutput = null;
        AndroidStockBlurImpl blurImpl = new AndroidStockBlurImpl();
        try {
            blurInput = Bitmap.createScaledBitmap(sourceBitmap, scaledWidth, scaledHeight, true);
            blurOutput = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            if (!blurImpl.prepare(this, blurInput, scriptRadius)) {
                return null;
            }
            blurImpl.blur(blurInput, blurOutput);
            return Bitmap.createScaledBitmap(blurOutput, sourceBitmap.getWidth(), sourceBitmap.getHeight(), true);
        } catch (Throwable e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to create cached accessory wallpaper blur", e);
            return null;
        } finally {
            blurImpl.release();
            if (blurInput != null && blurInput != sourceBitmap) {
                blurInput.recycle();
            }
            if (blurOutput != null) {
                blurOutput.recycle();
            }
        }
    }

    private void updateAccessoryRenderEffectBackdrop(@NonNull AccessoryRenderState state) {
        ImageView backdrop = findViewById(R.id.accessory_blur_backdrop);
        View surfaceHost = findViewById(R.id.accessory_surface_host);
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        boolean usingManagedWallpaperSource = shouldUseManagedWallpaperBlurSource();
        View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
        applyAccessoryLayerBounds(R.id.accessory_surface_host, null);
        if (backdrop == null || surfaceHost == null || accessoryContainer == null || wallpaperFrame == null ||
            accessoryContainer.getWidth() <= 0 || accessoryContainer.getHeight() <= 0) {
            if (backdrop != null && backdrop.getDrawable() != null && shouldUseAccessoryRenderEffectBlur(state)) {
                backdrop.setVisibility(View.VISIBLE);
            } else {
                clearAccessoryRenderEffectBackdrop();
            }
            return;
        }
        if (!shouldUseAccessoryRenderEffectBlur(state)) {
            clearAccessoryRenderEffectBackdrop();
            return;
        }
        if (mImeTransitionInProgress && backdrop.getDrawable() != null) {
            backdrop.setVisibility(View.VISIBLE);
            return;
        }

        int horizontalOverscanPx = computeAccessoryBackdropHorizontalOverscanPx(state.blurRadiusDp);
        applyAccessoryBackdropOverscan(backdrop, surfaceHost, horizontalOverscanPx);
        Rect backdropTargetRect = buildAccessoryBackdropTargetRect(surfaceHost, horizontalOverscanPx);
        if (!mAccessoryBackdropDirty &&
            mLastAccessoryBackdropBlurRadiusDp == state.blurRadiusDp &&
            mLastAccessoryBackdropManagedSource == usingManagedWallpaperSource &&
            mLastAccessoryBackdropTargetRect.equals(backdropTargetRect) &&
            backdrop.getDrawable() != null) {
            backdrop.setVisibility(View.VISIBLE);
            return;
        }
        Bitmap wallpaperBackdrop = createWallpaperBackdropBitmapForRect(backdropTargetRect, wallpaperFrame);
        if (wallpaperBackdrop == null) {
            if (backdrop.getDrawable() != null) {
                backdrop.setVisibility(View.VISIBLE);
            } else {
                clearAccessoryRenderEffectBackdrop();
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float blurRadiusPx = ViewUtils.dpToPx(this, Math.max(0, state.blurRadiusDp));
            backdrop.setImageBitmap(wallpaperBackdrop);
            backdrop.setRenderEffect(RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP));
        } else {
            Bitmap blurredBackdrop = createPreBlurredWallpaperBackdropBitmap(wallpaperBackdrop, state.blurRadiusDp);
            if (blurredBackdrop == null) {
                wallpaperBackdrop.recycle();
                if (backdrop.getDrawable() != null) {
                    backdrop.setVisibility(View.VISIBLE);
                } else {
                    clearAccessoryRenderEffectBackdrop();
                }
                return;
            }
            backdrop.setImageBitmap(blurredBackdrop);
            wallpaperBackdrop.recycle();
        }
        backdrop.setVisibility(View.VISIBLE);
        mAccessoryBackdropDirty = false;
        mLastAccessoryBackdropBlurRadiusDp = state.blurRadiusDp;
        mLastAccessoryBackdropManagedSource = usingManagedWallpaperSource;
        mLastAccessoryBackdropTargetRect.set(backdropTargetRect);
    }

    private void applyAccessoryRenderState(@NonNull AccessoryRenderState state) {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        View accessorySurfaceHost = findViewById(R.id.accessory_surface_host);
        View terminalToolbarViewPager = findViewById(R.id.terminal_toolbar_view_pager);
        View appsBarViewPager = findViewById(R.id.apps_bar_viewpager);
        View indicatorBand = findViewById(R.id.apps_bar_indicator_band);
        View extraKeysBackground = findViewById(R.id.extrakeys_background);
        View extraKeysBackgroundBlur = findViewById(R.id.extrakeys_backgroundblur);
        View bottomSpaceBackground = findViewById(R.id.activity_termux_bottom_space_background);
        View bottomSpaceBlur = findViewById(R.id.activity_termux_bottom_space_blur);
        View azRow = findViewById(R.id.apps_bar_az_row);
        View azFxUnderlay = findViewById(R.id.apps_bar_az_fx_underlay);
        View azFxOverlay = findViewById(R.id.apps_bar_az_fx_overlay);
        boolean useRenderEffectBlur = shouldUseAccessoryRenderEffectBlur(state);

        if (extraKeysBackgroundBlur != null && !useRenderEffectBlur) {
            applyRealtimeBlurRadius(extraKeysBackgroundBlur, state.blurRadiusDp);
            applyRealtimeBlurDownsampleFactor(extraKeysBackgroundBlur, ACCESSORY_BLUR_DOWNSAMPLE_FACTOR);
            applyRealtimeBlurOverlayColor(
                extraKeysBackgroundBlur,
                state.blurEnabled ? resolveAccessorySurfaceColor(state.barAlpha) : Color.TRANSPARENT
            );
        }
        if (!state.toolbarShown) {
            if (accessoryContainer != null) {
                accessoryContainer.setVisibility(View.GONE);
            }
            if (extraKeysBackgroundBlur != null) {
                extraKeysBackgroundBlur.setVisibility(View.GONE);
            }
            if (extraKeysBackground != null) {
                extraKeysBackground.setVisibility(View.GONE);
            }
            if (accessorySurfaceHost != null) {
                accessorySurfaceHost.setVisibility(View.GONE);
            }
            if (bottomSpaceBackground != null) {
                bottomSpaceBackground.setVisibility(View.GONE);
            }
            if (bottomSpaceBlur != null) {
                bottomSpaceBlur.setVisibility(View.GONE);
            }
            if (appsBarViewPager != null) {
                appsBarViewPager.setVisibility(View.GONE);
            }
            if (indicatorBand != null) {
                indicatorBand.setVisibility(View.GONE);
            }
            if (terminalToolbarViewPager != null) {
                terminalToolbarViewPager.setVisibility(View.GONE);
            }
            if (azRow != null) {
                azRow.setVisibility(View.GONE);
            }
            if (azFxOverlay != null) {
                azFxOverlay.setVisibility(View.GONE);
            }
            if (azFxUnderlay != null) {
                azFxUnderlay.setVisibility(View.GONE);
            }
            clearAccessoryRenderEffectBackdrop();
            configureAccessoryTopEdgeFx(false, state.barAlpha);
            resetAzOverflowAffordanceState();
            return;
        }

        if (accessoryContainer != null) {
            accessoryContainer.setVisibility(View.VISIBLE);
        }
        if (accessorySurfaceHost != null) {
            accessorySurfaceHost.setVisibility(View.VISIBLE);
        }
        if (appsBarViewPager != null) {
            appsBarViewPager.setVisibility(state.appsRowEnabled ? View.VISIBLE : View.GONE);
        }
        if (!state.appsRowEnabled) {
            mSuggestionBarExplicitSearchActive = false;
            resetAzGestureState(false, true);
        }
        if (indicatorBand != null) {
            indicatorBand.setVisibility(state.azRowEnabled ? View.VISIBLE : View.GONE);
        }
        if (terminalToolbarViewPager != null) {
            terminalToolbarViewPager.setVisibility(View.VISIBLE);
        }
        if (azRow != null) {
            azRow.setVisibility(state.azRowEnabled ? View.VISIBLE : View.GONE);
        }
        if (azFxUnderlay != null) {
            azFxUnderlay.setVisibility(View.GONE);
        }
        if (azFxOverlay != null) {
            azFxOverlay.setVisibility(View.GONE);
        }

        if (extraKeysBackground != null) {
            extraKeysBackground.setVisibility(View.VISIBLE);
            extraKeysBackground.setAlpha(state.barAlpha);
        }
        if (bottomSpaceBackground != null) {
            bottomSpaceBackground.setVisibility(View.GONE);
        }

        if (extraKeysBackgroundBlur != null) {
            extraKeysBackgroundBlur.setVisibility(state.blurEnabled && !useRenderEffectBlur ? View.VISIBLE : View.GONE);
        }
        if (bottomSpaceBackground != null) {
            bottomSpaceBackground.setVisibility(View.GONE);
        }
        if (bottomSpaceBlur != null) {
            bottomSpaceBlur.setVisibility(View.GONE);
        }
        configureAccessoryTopEdgeFx(true, state.barAlpha);
        updateAccessoryRenderEffectBackdrop(state);
        updateAzOverflowAffordance();
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

    private void applyRealtimeBlurOverlayColor(View blurView, int overlayColor) {
        if (!(blurView instanceof RealtimeBlurView)) {
            return;
        }
        ((RealtimeBlurView) blurView).setOverlayColor(overlayColor);
    }

    private boolean shouldUseWallpaperPassthroughMode() {
        return mPreferences != null
            && mPreferences.isUseSystemWallpaperEnabled();
    }

    private void syncTerminalWallpaperRenderingMode() {
        if (mTerminalView == null) {
            return;
        }
        mTerminalView.setUseTransparentFrameClear(false);
    }

    private boolean shouldEnableSeamlessStatusBackground() {
        return mProperties != null
            && !mProperties.isUsingFullScreen();
    }

    private boolean shouldShowTerminalStatusBarSurface(boolean showSurface, int terminalSurfaceColor) {
        return shouldEnableSeamlessStatusBackground()
            && showSurface
            && Color.alpha(terminalSurfaceColor) > 0
            && mLastStatusBarInsetTop > 0;
    }

    private void applyTerminalOverlayInsets(@NonNull WindowInsetsCompat insetsCompat) {
        int statusBarInsetTop = insetsCompat.getInsets(Type.statusBars()).top;
        mLastStatusBarInsetTop = statusBarInsetTop;

        View statusBarSurface = findViewById(R.id.terminal_status_bar_background);
        if (statusBarSurface != null) {
            ViewGroup.LayoutParams layoutParams = statusBarSurface.getLayoutParams();
            if (layoutParams != null && layoutParams.height != statusBarInsetTop) {
                layoutParams.height = statusBarInsetTop;
                statusBarSurface.setLayoutParams(layoutParams);
            }
        }

        applyTerminalSurfaceAppearance();
    }

    private void applySmoothDockImeOffset(int translationYPx) {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer == null) {
            return;
        }
        float translationY = Math.max(0, translationYPx);
        if (accessoryContainer.getTranslationY() != translationY) {
            accessoryContainer.setTranslationY(translationY);
        }
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
        mTermuxActivityRootView.marginBottom = 0;
        mTermuxActivityRootView.lastMarginBottom = null;
        mTermuxActivityRootView.lastMarginBottomTime = 0L;
        mTermuxActivityRootView.lastMarginBottomExtraTime = 0L;
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
        if (mSuggestionBarView != null) {
            mSuggestionBarView.setHostVisible(false);
        }
        removeTermuxActivityRootViewGlobalLayoutListener();
        removeAccessoryKeyboardLayoutListener();
        removeAccessoryLayoutChangeListeners();
        unregisterTermuxActivityBroadcastReceiver();
        unregisterPackageChangeReceiver();
        unregisterLauncherAppsCallback();
        mAccessoryRenderHandler.removeCallbacks(mAccessoryRenderSyncRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurHeartbeatRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurRecoveryRunnable);
        mAccessoryRenderHandler.removeCallbacks(mDeferredImeGeometryRunnable);
        mAccessoryRenderHandler.removeCallbacks(mRestoreAccessoryBlurAfterImeRunnable);
        mAccessoryRenderSyncPending = false;
        mPendingAccessoryRenderReason = null;
        mImeTransitionInProgress = false;
        mFadeAccessoryBlurAfterImeRestore = false;
        setAccessoryBlurLayerAlpha(1f);
        applySmoothDockImeOffset(0);
        clearAccessoryRenderEffectBackdrop();
        mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
        mAzGestureHandler.removeCallbacks(mLauncherCatalogWarmRunnable);
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logDebug(LOG_TAG, "onDestroy");
        if (mIsInvalidState)
            return;
        clearAccessoryRenderEffectBackdrop();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.releaseResources();
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
        updateWindowBackgroundForCurrentSession();
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
                if (!recoverEmptyVisibleSessionInPlace(intent, "service-connected")) {
                    startBootstrapAndSession(intent);
                }
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
            if (mTermuxService == null) {
                mEmptySessionRecoveryInProgress = false;
                return;
            }
            try {
                boolean launchFailsafe = false;
                if (intent != null && intent.getExtras() != null) {
                    launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                }
                mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                mEmptySessionRecoveryInProgress = false;
            } catch (WindowManager.BadTokenException e) {
                // Activity finished - ignore.
                mEmptySessionRecoveryInProgress = false;
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
        recoverEmptyVisibleSessionInPlace(null, source);
    }

    private boolean shouldRecoverEmptySessionInPlace() {
        return mIsVisible && (mLastLaunchWasLauncherEntry || isTaskRoot() || isDefaultHomeApp());
    }

    private void resetUiForInPlaceSessionRecovery(@NonNull String reason) {
        Logger.logWarn(LOG_TAG, "Resetting launcher UI before in-place empty-session recovery from " + reason);
        if (mTerminalActionDialog != null) {
            mTerminalActionDialog.dismiss();
            mTerminalActionDialog = null;
        }
        if (mSuggestionBarView != null) {
            mSuggestionBarView.resetTransientVisualState();
            mSuggestionBarView.clearAzPreview();
        }
        stopAzEdgePagingLoop();
        cancelAzOverflowRefresh();
        mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryRenderSyncRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurHeartbeatRunnable);
        mAccessoryRenderHandler.removeCallbacks(mDeferredImeGeometryRunnable);
        mAccessoryRenderSyncPending = false;
        mPendingAccessoryRenderReason = null;
        if (mTerminalView != null) {
            mTerminalView.onContextMenuClosed(null);
        }
        getDrawer().closeDrawers();
    }

    public boolean recoverEmptyVisibleSessionInPlace(@Nullable Intent intent, @NonNull String reason) {
        if (!shouldRecoverEmptySessionInPlace() || mTermuxService == null || mTermuxTerminalSessionActivityClient == null) {
            return false;
        }
        if (!mTermuxService.isTermuxSessionsEmpty()) {
            mEmptySessionRecoveryInProgress = false;
            return true;
        }
        if (mEmptySessionRecoveryInProgress) {
            Logger.logWarn(LOG_TAG, "Ignoring duplicate empty-session recovery while one is already running: " + reason);
            return true;
        }
        long now = SystemClock.elapsedRealtime();
        if (mLastEmptySessionRecoveryElapsedMs > 0
            && (now - mLastEmptySessionRecoveryElapsedMs) < EMPTY_SESSION_RECOVERY_DEBOUNCE_MS) {
            Logger.logWarn(LOG_TAG, "Ignoring empty-session recovery during debounce window: " + reason);
            return true;
        }
        mLastEmptySessionRecoveryElapsedMs = now;
        mEmptySessionRecoveryInProgress = true;
        Logger.logWarn(LOG_TAG, "No active terminal session while visible Home launcher; recovering in-place from " + reason);
        resetUiForInPlaceSessionRecovery(reason);
        startBootstrapAndSession(intent);
        return true;
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

        boolean useWallpaperTheme = shouldUseWallpaperPassthroughMode();
        setTheme(useWallpaperTheme ? R.style.Theme_TermuxActivity_Wallpaper : R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        TermuxThemeManager.applyThemeOverlays(this);
        Logger.logDebug(LOG_TAG, "Applied " + (useWallpaperTheme ? "wallpaper" : "normal") + " theme");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = shouldUseWallpaperPassthroughMode() ? 0 : mProperties.getTerminalMarginHorizontal();
        int marginVertical = shouldUseWallpaperPassthroughMode() ? 0 : mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    private void setSuggestionBarView() {
        final FrameLayout appsBarContainer = findViewById(R.id.apps_bar_viewpager);
        mAzScrubRowView = findViewById(R.id.apps_bar_az_row);
        mLauncherAzGestureFxUnderlayView = findViewById(R.id.apps_bar_az_fx_underlay);
        mLauncherAzGestureFxOverlayView = findViewById(R.id.apps_bar_az_fx_overlay);
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setRenderLayer(LauncherAzGestureFxView.RenderLayer.UNDERLAY);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setRenderLayer(LauncherAzGestureFxView.RenderLayer.OVERLAY);
        }
        if (appsBarContainer == null) {
            return;
        }
        appsBarContainer.setClipChildren(false);
        appsBarContainer.setClipToPadding(false);
        ViewParent vpParent = appsBarContainer.getParent();
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

        if (mSuggestionBarView == null) {
            LayoutInflater inflater = LayoutInflater.from(TermuxActivity.this);
            mSuggestionBarView = (SuggestionBarView) inflater.inflate(R.layout.suggestion_bar, appsBarContainer, false);
        } else if (mSuggestionBarView.getParent() instanceof ViewGroup) {
            ((ViewGroup) mSuggestionBarView.getParent()).removeView(mSuggestionBarView);
        }

        if (mSuggestionBarView.getParent() != appsBarContainer) {
            appsBarContainer.removeAllViews();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            appsBarContainer.addView(mSuggestionBarView, params);
        }

        mSuggestionBarView.setAppDataProvider(mLauncherAppDataProvider);
        mSuggestionBarView.setConfigRepository(mLauncherConfigRepository);
        mSuggestionBarView.setAppCatalogChangedListener(this::syncAzScrubLettersAndTint);
        applySuggestionBarPreferences();
        applyDockLayoutMetrics(buildDockLayoutMetrics(0));
        if (isSuggestionBarEnabled()) {
            mSuggestionBarView.reload();
        }
        mSuggestionBarView.post(() -> {
            if (mSuggestionBarView == null || !mIsVisible || !isSuggestionBarEnabled()) {
                return;
            }
            scheduleLauncherCatalogWarmup();
        });
        if (mTermuxTerminalViewClient != null) {
            mTermuxTerminalViewClient.setSuggestionBarCallback(this);
        }

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

    private boolean isLauncherHomeIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())) {
            return false;
        }
        Set<String> categories = intent.getCategories();
        if (categories == null || categories.isEmpty()) {
            return false;
        }
        return categories.contains(Intent.CATEGORY_HOME) || categories.contains(Intent.CATEGORY_LAUNCHER);
    }

    static boolean shouldShowInRecents(boolean showWhenNotHomeEnabled, boolean isDefaultHome) {
        return showWhenNotHomeEnabled && !isDefaultHome;
    }

    private boolean isDefaultHomeApp() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        PackageManager packageManager = getPackageManager();
        ResolveInfo resolveInfo = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return false;
        }
        String homePackage = resolveInfo.activityInfo.packageName;
        return !TextUtils.isEmpty(homePackage) && getPackageName().equals(homePackage);
    }

    private boolean shouldShowInRecents() {
        return mPreferences != null
            && shouldShowInRecents(mPreferences.isRemoveTaskOnActivityFinishEnabled(), isDefaultHomeApp());
    }

    private void syncRecentsVisibilityPolicy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        boolean excludeFromRecents = !shouldShowInRecents();
        try {
            if (getTaskId() != -1) {
                for (android.app.ActivityManager.AppTask appTask : getSystemService(android.app.ActivityManager.class).getAppTasks()) {
                    if (appTask == null || appTask.getTaskInfo() == null || appTask.getTaskInfo().taskId != getTaskId()) {
                        continue;
                    }
                    appTask.setExcludeFromRecents(excludeFromRecents);
                    break;
                }
            }
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Failed to sync recents visibility: " + throwable.getMessage());
        }
    }

    private char getSuggestionBarSplitChar() {
        if (mPreferences == null) {
            return ' ';
        }
        String inputChar = mPreferences.getAppLauncherInputChar();
        if (inputChar == null) {
            return ' ';
        }
        String trimmed = inputChar.trim();
        return trimmed.isEmpty() ? ' ' : trimmed.charAt(0);
    }

    private boolean isSuggestionBarEnabled() {
        return mPreferences != null && mPreferences.isAppLauncherAppsRowEnabled();
    }

    private boolean isAzRowEnabled() {
        return isSuggestionBarEnabled() && mPreferences.isAppLauncherAzRowEnabled();
    }

    public boolean shouldProcessSuggestionBarKeyEvent(int keyCode) {
        if (!isSuggestionBarEnabled() || mSuggestionBarView == null) {
            return false;
        }
        if (keyCode == android.view.KeyEvent.KEYCODE_DEL || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
            return mSuggestionBarExplicitSearchActive || mSuggestionBarView.isSearchSurfaceActive();
        }
        return false;
    }

    public boolean shouldProcessSuggestionBarCodePoint(int codePoint, boolean ctrlDown) {
        if (ctrlDown || !isSuggestionBarEnabled() || mSuggestionBarView == null) {
            return false;
        }
        if (mSuggestionBarExplicitSearchActive || mSuggestionBarView.isSearchSurfaceActive()) {
            return true;
        }
        char[] chars = Character.toChars(codePoint);
        return chars.length == 1 && chars[0] == getSuggestionBarSplitChar();
    }

    public boolean shouldDelayRootMarginAdjustments() {
        return SystemClock.uptimeMillis() < mDelayRootMarginAdjustmentsUntilUptimeMs;
    }

    public boolean shouldDelaySoftKeyboardShowOnResume() {
        return isDefaultHomeApp() && isLauncherHomeIntent(getIntent());
    }

    private void applyAccessoryGeometryIfNeeded(boolean force, @NonNull String reason) {
        long now = SystemClock.uptimeMillis();
        if (!force && (now - mLastAccessoryGeometryApplyUptimeMs) < 120L) {
            scheduleAccessoryRenderSync(reason + ":skip");
            return;
        }
        mLastAccessoryGeometryApplyUptimeMs = now;
        updateAppLauncherBarHeight();
        setTerminalToolbarHeight(true);
        configureExtraKeysBackground();
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
        mSuggestionBarView.setIconScale(resolveDerivedDockIconScale());
        mSuggestionBarView.setDockRowHeightHintPx(buildDockLayoutMetrics(0).appsBarHeightPx);
        mSuggestionBarView.setAppBarOpacity(mPreferences.getAppBarOpacity());
        int blurRadiusDp = getEffectiveExtraKeysBlurRadius();
        mSuggestionBarView.setBlurConfig(blurRadiusDp > 0, blurRadiusDp);
        mSuggestionBarView.setInheritedTintColor(resolveAccessoryGlassBaseColor());
        mSuggestionBarView.setAppDataProvider(mLauncherAppDataProvider);
        mSuggestionBarView.setConfigRepository(mLauncherConfigRepository);
        mSuggestionBarView.setAppCatalogChangedListener(this::syncAzScrubLettersAndTint);
        mSuggestionBarView.setOverflowInteractionListener(this::onSuggestionBarOverflowInteractionChanged);
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.onAnimationPreferenceUpdated();
        }
        if (!isSuggestionBarEnabled()) {
            mSuggestionBarExplicitSearchActive = false;
            resetAzGestureState(false, true);
            resetAzOverflowAffordanceState();
            return;
        }
        mSuggestionBarView.reloadAllApps();
        String input = "";
        if (mTerminalView != null && mSuggestionBarExplicitSearchActive) {
            input = normalizeSuggestionBarInput(mTerminalView.getCurrentInput());
        }
        mSuggestionBarView.reloadWithInput(input, mTerminalView);
        syncAzScrubLettersAndTint();
    }

    private void onSuggestionBarOverflowInteractionChanged(boolean interacting) {
        mSuggestionBarInteractionActive = interacting;
        updateAzOverflowAffordance();
    }

    private void syncAzScrubLettersAndTint() {
        if (!isAzRowEnabled() || mAzScrubRowView == null || mSuggestionBarView == null) return;
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
        if (!isAzRowEnabled() || mSuggestionBarView == null || mAzScrubRowView == null) {
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
        populateRawBounds(findViewById(R.id.apps_bar_indicator_band), mIndicatorBandRawBounds);
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
        if (!isAzRowEnabled()) {
            return;
        }
        if (mLauncherAzGestureFxUnderlayView == null && mLauncherAzGestureFxOverlayView == null) {
            return;
        }
        populateRawBounds(mAzScrubRowView, mAzRowRawBounds);
        populateRawBounds(mSuggestionBarView, mAppsRowRawBounds);
        populateRawBounds(findViewById(R.id.apps_bar_indicator_band), mIndicatorBandRawBounds);
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
        if (!isSuggestionBarEnabled()) {
            resetAzOverflowAffordanceState();
            return;
        }
        if ((mLauncherAzGestureFxUnderlayView == null && mLauncherAzGestureFxOverlayView == null) || mSuggestionBarView == null) {
            return;
        }
        populateRawBounds(mAzScrubRowView, mAzRowRawBounds);
        populateRawBounds(mSuggestionBarView, mAppsRowRawBounds);
        populateRawBounds(findViewById(R.id.apps_bar_indicator_band), mIndicatorBandRawBounds);
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
            showPageIndicators = true;
            subtlePinnedIndicators = true;
        } else if (!mAzGestureActive && !mSuggestionBarInteractionActive && mSuggestionBarView.hasPinnedOverflowPages()) {
            canLeft = mSuggestionBarView.canPinnedPageLeft();
            canRight = mSuggestionBarView.canPinnedPageRight();
            currentPageIndex = mSuggestionBarView.getPinnedCurrentPageIndex();
            pageCount = mSuggestionBarView.getPinnedVisiblePageCount();
            showPageIndicators = true;
            interactionActive = true;
            subtlePinnedIndicators = true;
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
            mLauncherAzGestureFxUnderlayView.setRowBounds(mAzRowRawBounds, mAppsRowRawBounds, mIndicatorBandRawBounds, mExtraKeysRawBounds);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setRowBounds(mAzRowRawBounds, mAppsRowRawBounds, mIndicatorBandRawBounds, mExtraKeysRawBounds);
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

    private void resetAzOverflowAffordanceState() {
        mSuggestionBarInteractionActive = false;
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.clearDrag(false);
            mLauncherAzGestureFxUnderlayView.setVisibility(View.GONE);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.clearDrag(false);
            mLauncherAzGestureFxOverlayView.setVisibility(View.GONE);
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
        if (!isAzRowEnabled() || !mAzGestureActive || mAzGestureMode != AzGestureMode.ICON_TRACKING_LOCKED || focusResult == null) {
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
        if (!isSuggestionBarEnabled()) {
            return;
        }
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
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(this, R.color.termux_primary));
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
        if (mPreferences == null || !mPreferences.isAppLauncherAzRowEnabled()) {
            return;
        }
        String method = mPreferences.getAppLauncherAzLockMethod();
        if (TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU.equals(method)) {
            lockScreenWithShizuku();
        } else if (TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_ACCESSIBILITY.equals(method)) {
            lockScreenWithAccessibility();
        }
    }

    private void refreshShizukuLockBackendIfNeeded() {
        if (mPreferences == null) {
            return;
        }
        String method = mPreferences.getAppLauncherAzLockMethod();
        if (!TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU.equals(method)) {
            return;
        }
        PrivilegedBackendManager.getInstance().initializeShizukuOnly(this)
            .exceptionally(throwable -> {
                Logger.logWarn(LOG_TAG, "A-Z Shizuku backend refresh failed: " + throwable.getMessage());
                return false;
            });
    }

    private void lockScreenWithShizuku() {
        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        manager.initializeShizukuOnly(this)
            .thenAccept(available -> {
                if (!available || !manager.isPrivilegedAvailable()) {
                    manager.requestPrivilegedPermission(ShizukuBackend.PERMISSION_REQUEST_CODE);
                    return;
                }
                executeShizukuLockCommand(manager);
            })
            .exceptionally(throwable -> {
                Logger.logWarn(LOG_TAG, "A-Z Shizuku lock initialization failed: " + throwable.getMessage());
                return null;
            });
    }

    private void executeShizukuLockCommand(@NonNull PrivilegedBackendManager manager) {
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

    private void lockScreenWithAccessibility() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            showEnableAccessibilityLockDialog();
            return;
        }
        if (isLockAccessibilityServiceEnabled() && LockAccessibilityService.lockScreen()) {
            return;
        }
        showEnableAccessibilityLockDialog();
    }

    private boolean isLockAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }
        ComponentName componentName = new ComponentName(this, LockAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName enabled = ComponentName.unflattenFromString(splitter.next());
            if (componentName.equals(enabled)) {
                return true;
            }
        }
        return false;
    }

    private void showEnableAccessibilityLockDialog() {
        runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.termux_app_launcher_accessibility_lock_prompt_title)
            .setMessage(R.string.termux_app_launcher_accessibility_lock_prompt_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.termux_app_launcher_accessibility_lock_prompt_enable, (dialog, which) -> {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.termux_app_launcher_set_home_unavailable, Toast.LENGTH_SHORT).show();
                }
            })
            .show());
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
        mTerminalView.setSplitChar(getSuggestionBarSplitChar());
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
        syncTerminalWallpaperRenderingMode();
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
        mTermuxTerminalExtraKeys2 = null;
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;
        updateAppLauncherBarHeight();
        setTerminalToolbarHeight();
        configureExtraKeysBackground();
        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);
        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
        scheduleAccessoryRenderSync("setTerminalToolbarView");
    }

    private void updateAppLauncherBarHeight() {
        if (mPreferences == null)
            return;
        applyDockLayoutMetrics(buildDockLayoutMetrics(0));
    }

    public void setTerminalToolbarHeight() {
        setTerminalToolbarHeight(true);
    }

    private void setTerminalToolbarHeight(boolean requestTerminalResize) {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        View accessoryStackContainer = findViewById(R.id.accessory_stack_container);
        if (terminalToolbarViewPager == null || accessoryStackContainer == null)
            return;
        ViewGroup.LayoutParams toolbarLayoutParams = terminalToolbarViewPager.getLayoutParams();

        int matrix = 0;
        if (mTermuxTerminalExtraKeys != null && mTermuxTerminalExtraKeys.getExtraKeysInfo() != null) {
            matrix = mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length;
        }

        int toolbarHeightPx = Math.round(mTerminalToolbarDefaultHeight * matrix * mProperties.getTerminalToolbarHeightScaleFactor());
        toolbarLayoutParams.height = toolbarHeightPx;
        terminalToolbarViewPager.setLayoutParams(toolbarLayoutParams);

        DockLayoutMetrics dockMetrics = buildDockLayoutMetrics(0);
        applyDockLayoutMetrics(dockMetrics);
        int combinedHeight = dockMetrics.combinedHeight(toolbarHeightPx);
        boolean accessoryHeightChanged = updateAccessoryStackContainerHeight(accessoryStackContainer, combinedHeight);
        if (requestTerminalResize && accessoryHeightChanged && mTerminalView != null) {
            mTerminalView.post(mTerminalView::updateSize);
        }
        scheduleAccessoryRenderSync("setTerminalToolbarHeight");
    }

    private boolean updateAccessoryStackContainerHeight(View view, int height) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams == null)
            return false;
        if (layoutParams.height == height)
            return false;
        layoutParams.height = height;
        view.setLayoutParams(layoutParams);
        return true;
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

    private int getDockBaseToolbarHeightPx() {
        if (mTerminalToolbarDefaultHeight > 0) {
            return Math.round(mTerminalToolbarDefaultHeight);
        }
        return Math.round(getResources().getDisplayMetrics().density * 37.5f);
    }

    @NonNull
    private DockLayoutMetrics buildDockLayoutMetrics(int additionalAppsBarHeightPx) {
        if (mPreferences == null) {
            return new DockLayoutMetrics(0, 0, 0, 0);
        }

        float density = getResources().getDisplayMetrics().density;
        float barHeightScale = mPreferences.getAppLauncherBarHeightScale();
        float normalizedScale = Math.max(0f, Math.min(1f, (barHeightScale - 1.45f) / (2.18f - 1.45f)));
        boolean appsRowEnabled = mPreferences.isAppLauncherAppsRowEnabled();
        int appsBarHeightPx = appsRowEnabled
            ? Math.max(0, Math.round(getDockBaseToolbarHeightPx() * (1.08f + (normalizedScale * 0.30f))) + Math.max(0, additionalAppsBarHeightPx))
            : 0;

        boolean azEnabled = appsRowEnabled && mPreferences.isAppLauncherAzRowEnabled();
        int azRowHeightPx = 0;
        int indicatorBandHeightPx = 0;
        if (azEnabled) {
            azRowHeightPx = Math.round(22f * density);
            indicatorBandHeightPx = Math.round(10f * density);
        }

        int interRowGapPx = indicatorBandHeightPx;

        return new DockLayoutMetrics(appsBarHeightPx, indicatorBandHeightPx, azRowHeightPx, interRowGapPx);
    }

    private float resolveDerivedDockIconScale() {
        if (mPreferences == null) {
            return 1.36f;
        }
        float barHeightScale = mPreferences.getAppLauncherBarHeightScale();
        float normalized = Math.max(0f, Math.min(1f, (barHeightScale - 1.45f) / (2.18f - 1.45f)));
        return 1.34f + (normalized * 0.64f);
    }

    private void applyDockLayoutMetrics(@NonNull DockLayoutMetrics metrics) {
        updateViewHeight(R.id.apps_bar_viewpager, metrics.appsBarHeightPx);
        updateViewHeight(R.id.apps_bar_indicator_band, metrics.indicatorBandHeightPx);
        updateViewHeight(R.id.apps_bar_az_row, metrics.azRowHeightPx);
        updateViewBottomMargin(R.id.apps_bar_viewpager, 0);
        if (mSuggestionBarView != null) {
            mSuggestionBarView.setDockRowHeightHintPx(metrics.appsBarHeightPx);
        }
    }

    public void toggleTerminalToolbar() {
        boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar), true);

        configureExtraKeysBackground();
        scheduleAccessoryRenderSync("toggleTerminalToolbar");

        isToolbarHidden = !showNow;
    
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
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

    private void registerWallpaperActivityResultLaunchers() {
        mWallpaperPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) {
                    launchWallpaperCrop(uri);
                }
            }
        );
        mWallpaperCropLauncher = registerForActivityResult(
            new CropImageContract(),
            result -> {
                if (result instanceof CropImage.CancelledResult) {
                    return;
                }
                handleWallpaperCropResult(result);
            }
        );
    }

    private void launchManagedWallpaperPicker() {
        if (mWallpaperPickerLauncher == null) {
            showToast(getString(R.string.error_wallpaper_set_failed), true);
            return;
        }
        mWallpaperPickerLauncher.launch(
            new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()
        );
    }

    private void launchWallpaperCrop(@NonNull Uri sourceUri) {
        if (mWallpaperCropLauncher == null) {
            showToast(getString(R.string.error_wallpaper_set_failed), true);
            return;
        }

        Rect wallpaperFrameRect = getSystemWallpaperFrameRect();
        CropImageOptions cropOptions = new CropImageOptions();
        cropOptions.fixAspectRatio = true;
        cropOptions.aspectRatioX = Math.max(1, wallpaperFrameRect.width());
        cropOptions.aspectRatioY = Math.max(1, wallpaperFrameRect.height());
        cropOptions.outputRequestWidth = Math.max(1, wallpaperFrameRect.width());
        cropOptions.outputRequestHeight = Math.max(1, wallpaperFrameRect.height());
        File tempCropFile = getManagedWallpaperTempFile();
        if (tempCropFile.exists()) {
            tempCropFile.delete();
        }
        cropOptions.customOutputUri = getManagedWallpaperTempFileUri(tempCropFile);
        cropOptions.outputCompressFormat = Bitmap.CompressFormat.PNG;
        cropOptions.outputCompressQuality = 100;
        cropOptions.activityTitle = "";
        cropOptions.cropMenuCropButtonTitle = getString(R.string.action_apply);
        cropOptions.activityBackgroundColor = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        cropOptions.backgroundColor = withAlphaComponent(Color.BLACK, 176);
        cropOptions.guidelinesColor = withAlphaComponent(Color.WHITE, 190);
        cropOptions.toolbarColor = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh, R.color.termux_surface_panel_high);
        cropOptions.toolbarTitleColor = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurface, R.color.termux_on_surface);
        cropOptions.toolbarBackButtonColor = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurface, R.color.termux_on_surface);
        cropOptions.toolbarTintColor = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurface, R.color.termux_on_surface);
        cropOptions.activityMenuIconColor = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurface, R.color.termux_on_surface);
        cropOptions.activityMenuTextColor = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurface, R.color.termux_on_surface);

        mWallpaperCropLauncher.launch(new CropImageContractOptions(sourceUri, cropOptions));
    }

    private void handleWallpaperCropResult(@NonNull CropImageView.CropResult result) {
        if (!result.isSuccessful()) {
            Logger.logError(LOG_TAG, "Wallpaper crop failed");
            if (result.getError() != null) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Wallpaper crop failed", result.getError());
            }
            showToast(getString(R.string.error_wallpaper_set_failed), true);
            return;
        }

        Uri croppedUri = result.getUriContent();
        if (croppedUri == null) {
            showToast(getString(R.string.error_wallpaper_set_failed), true);
            return;
        }

        showWallpaperTargetPrompt(croppedUri);
    }

    private void showWallpaperTargetPrompt(@NonNull Uri croppedUri) {
        String[] targets = new String[] {
            getString(R.string.wallpaper_target_home_screen),
            getString(R.string.wallpaper_target_lock_screen),
            getString(R.string.wallpaper_target_home_and_lock_screen)
        };
        int[] flags = new int[] {
            WallpaperManager.FLAG_SYSTEM,
            WallpaperManager.FLAG_LOCK,
            WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, targets);
        new MaterialAlertDialogBuilder(this)
            .setAdapter(adapter, (dialogInterface, which) -> {
                int selectedFlags = flags[which];
                if (!applyManagedWallpaper(croppedUri, selectedFlags)) {
                    showToast(getString(R.string.error_wallpaper_set_failed), true);
                    return;
                }

                if ((selectedFlags & WallpaperManager.FLAG_SYSTEM) != 0) {
                    setWallpaperModeEnabled(this, true);
                    updateWindowBackgroundForCurrentSession();
                    View rootView = findViewById(R.id.activity_termux_root_view);
                    if (rootView != null) {
                        rootView.post(this::applyWallpaperOffsetFixIfNeeded);
                    }
                    scheduleAccessoryRenderSync("wallpaper-crop-applied");
                }
            })
            .show();
    }

    private boolean applyManagedWallpaper(@NonNull Uri croppedUri, int wallpaperFlags) {
        Rect visibleCropHint = getWallpaperFullImageCropHint(croppedUri);
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
            if ((wallpaperFlags & WallpaperManager.FLAG_SYSTEM) != 0) {
                suggestManagedWallpaperDimensions(wallpaperManager);
            }
            if (!setManagedWallpaperStream(wallpaperManager, croppedUri, visibleCropHint, wallpaperFlags)) {
                return false;
            }
            exportWallpaperCopyToTermuxBackgroundDirectory(croppedUri);
            if ((wallpaperFlags & WallpaperManager.FLAG_SYSTEM) != 0) {
                promoteManagedWallpaperTempFile();
                clearManagedWallpaperWindowBackgroundCache();
                int wallpaperId = getCurrentSystemWallpaperId();
                if (mPreferences != null) {
                    mPreferences.setManagedWallpaperSystemId(wallpaperId);
                }
            }
            return true;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply managed wallpaper", e);
            return false;
        }
    }

    private boolean setManagedWallpaperStream(@NonNull WallpaperManager wallpaperManager, @NonNull Uri croppedUri,
                                              @Nullable Rect visibleCropHint, int wallpaperFlags) {
        if (visibleCropHint != null) {
            try (InputStream inputStream = openWallpaperInputStream(croppedUri)) {
                if (inputStream == null) {
                    return false;
                }
                wallpaperManager.setStream(inputStream, visibleCropHint, true, wallpaperFlags);
                return true;
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply managed wallpaper with crop hint; retrying without hint", e);
            }
        }

        try (InputStream inputStream = openWallpaperInputStream(croppedUri)) {
            if (inputStream == null) {
                return false;
            }
            wallpaperManager.setStream(inputStream, null, true, wallpaperFlags);
            return true;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply managed wallpaper without crop hint", e);
            return false;
        }
    }

    @Nullable
    private Rect getWallpaperFullImageCropHint(@NonNull Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream inputStream = openWallpaperInputStream(uri)) {
            if (inputStream == null) {
                return null;
            }
            BitmapFactory.decodeStream(inputStream, null, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null;
            }
            return new Rect(0, 0, options.outWidth, options.outHeight);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read wallpaper crop bounds", e);
            return null;
        }
    }

    private void exportWallpaperCopyToTermuxBackgroundDirectory(@NonNull Uri wallpaperUri) {
        File backgroundDir = TermuxConstants.TERMUX_BACKGROUND_DIR;
        if (!backgroundDir.exists() && !backgroundDir.mkdirs()) {
            Logger.logError(LOG_TAG, "Failed to create termux background directory at: " + backgroundDir.getAbsolutePath());
            return;
        }

        File destination = TermuxConstants.TERMUX_BACKGROUND_IMAGE_FILE;
        try (InputStream inputStream = openWallpaperInputStream(wallpaperUri);
             FileOutputStream outputStream = new FileOutputStream(destination, false)) {
            if (inputStream == null) {
                Logger.logError(LOG_TAG, "Failed to export wallpaper copy: could not open source stream");
                return;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to export wallpaper copy to " + destination.getAbsolutePath(), e);
        }
    }

    @Nullable
    private InputStream openWallpaperInputStream(@NonNull Uri uri) {
        try {
            if ("file".equals(uri.getScheme())) {
                return new FileInputStream(new File(uri.getPath()));
            }
            return getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to open wallpaper stream", e);
            return null;
        }
    }

    private int getCurrentSystemWallpaperId() {
        try {
            return WallpaperManager.getInstance(this).getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to resolve current system wallpaper id", e);
            return -1;
        }
    }

    @NonNull
    private File getManagedWallpaperExactFile() {
        File directory = new File(getFilesDir(), "managed-wallpaper");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return new File(directory, "system-wallpaper-exact.png");
    }

    @NonNull
    private File getManagedWallpaperTempFile() {
        File directory = new File(getFilesDir(), "managed-wallpaper");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return new File(directory, "system-wallpaper-pending.png");
    }

    @NonNull
    private Uri getManagedWallpaperTempFileUri(@NonNull File file) {
        return FileProvider.getUriForFile(
            this,
            getPackageName() + ".cropper.fileprovider",
            file
        );
    }

    private void promoteManagedWallpaperTempFile() {
        File tempFile = getManagedWallpaperTempFile();
        if (!tempFile.isFile()) {
            return;
        }
        File exactFile = getManagedWallpaperExactFile();
        if (exactFile.exists()) {
            exactFile.delete();
        }
        if (!tempFile.renameTo(exactFile)) {
            Logger.logError(LOG_TAG, "Failed to promote managed wallpaper temp file");
        }
    }

    private void suggestManagedWallpaperDimensions(@NonNull WallpaperManager wallpaperManager) {
        try {
            Rect frameRect = getSystemWallpaperFrameRect();
            wallpaperManager.suggestDesiredDimensions(frameRect.width(), frameRect.height());
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to suggest wallpaper dimensions; continuing with wallpaper apply", e);
        }
    }

    @NonNull
    private Rect getManagedWallpaperFrameRect() {
        if (getWindow() != null && getWindow().getDecorView() != null) {
            View decorView = getWindow().getDecorView();
            if (decorView.getWidth() > 0 && decorView.getHeight() > 0) {
                decorView.getLocationOnScreen(mTmpParentLocation);
                int left = mTmpParentLocation[0];
                int top = mTmpParentLocation[1];
                return new Rect(left, top, left + decorView.getWidth(), top + decorView.getHeight());
            }
        }
        return getSystemWallpaperFrameRect();
    }

    @NonNull
    private Rect getSystemWallpaperFrameRect() {
        DisplayMetrics realMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(realMetrics);
        return new Rect(0, 0, Math.max(1, realMetrics.widthPixels), Math.max(1, realMetrics.heightPixels));
    }

    public static void setWallpaperModeEnabled(@NonNull Context context, boolean enabled) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        if (preferences == null) {
            return;
        }

        if (enabled) {
            preferences.setUseSystemWallpaperEnabled(true);
            preferences.setTerminalBackgroundOpacity(preferences.getWallpaperEnabledTerminalBackgroundOpacity());
            preferences.setAppBarOpacity(preferences.getWallpaperEnabledAppBarOpacity());
        } else {
            preferences.setWallpaperEnabledTerminalBackgroundOpacity(preferences.getTerminalBackgroundOpacity());
            preferences.setWallpaperEnabledAppBarOpacity(preferences.getAppBarOpacity());
            preferences.setUseSystemWallpaperEnabled(false);
            preferences.setTerminalBackgroundOpacity(100);
            preferences.setAppBarOpacity(100);
        }

        requestTermuxActivityStylingOnNextResume(context, true);
    }

    private void openLookAndFeelSettings() {
        ActivityUtils.startActivity(this,
            SettingsActivity.createFragmentIntent(this,
                com.termux.app.fragments.settings.termux.TermuxStylePreferencesFragment.class,
                R.string.termux_style_preferences_title));
    }

    private void openAppsBarSettings() {
        ActivityUtils.startActivity(this,
            SettingsActivity.createFragmentIntent(this,
                com.termux.app.fragments.settings.termux.LauncherPreferencesFragment.class,
                R.string.termux_launcher_preferences_title));
    }

    private void openSettingsHome() {
        ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
    }

    private boolean handleTerminalAction(int itemId) {
        TerminalSession session = getCurrentSession();
        switch(itemId) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SET_WALLPAPER_ID:
                launchManagedWallpaperPicker();
                return true;
            case CONTEXT_MENU_REMOVE_WALLPAPER_ID:
                setWallpaperModeEnabled(this, !shouldUseWallpaperPassthroughMode());
                return true;
            case CONTEXT_MENU_LOOK_AND_FEEL_ID:
                openLookAndFeelSettings();
                return true;
            case CONTEXT_MENU_APPS_BAR_ID:
                openAppsBarSettings();
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                openSettingsHome();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            default:
                return false;
        }
    }

    public boolean showTerminalActionSheet() {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) {
            return false;
        }
        if (mTerminalActionDialog != null && mTerminalActionDialog.isShowing()) {
            return true;
        }
        List<TerminalActionItem> items = new ArrayList<>();
        items.add(new TerminalActionItem(CONTEXT_MENU_SELECT_URL_ID, getString(R.string.action_select_url)));
        items.add(new TerminalActionItem(CONTEXT_MENU_SHARE_TRANSCRIPT_ID, getString(R.string.action_share_transcript)));
        items.add(new TerminalActionItem(CONTEXT_MENU_SET_WALLPAPER_ID, getString(R.string.action_set_background_image)));
        items.add(new TerminalActionItem(
            CONTEXT_MENU_REMOVE_WALLPAPER_ID,
            getString(shouldUseWallpaperPassthroughMode()
                ? R.string.action_disable_background_image
                : R.string.action_enable_background_image)
        ));
        items.add(new TerminalActionItem(CONTEXT_MENU_LOOK_AND_FEEL_ID, getString(R.string.action_look_and_feel)));
        items.add(new TerminalActionItem(CONTEXT_MENU_APPS_BAR_ID, getString(R.string.action_apps_bar)));
        items.add(new TerminalActionItem(CONTEXT_MENU_SETTINGS_ID, getString(R.string.action_open_settings)));
        items.add(new TerminalActionItem(CONTEXT_MENU_RESET_TERMINAL_ID, getString(R.string.action_reset_terminal)));
        items.add(new TerminalActionItem(CONTEXT_MENU_KILL_PROCESS_ID,
            getString(R.string.action_kill_process, currentSession.getPid())));

        ArrayAdapter<TerminalActionItem> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_1, items);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setAdapter(adapter, (dialogInterface, which) -> handleTerminalAction(items.get(which).id))
            .setOnDismissListener(dialogInterface -> {
                if (mTerminalView != null) {
                    mTerminalView.onContextMenuClosed(null);
                }
                if (mTerminalActionDialog == dialogInterface) {
                    mTerminalActionDialog = null;
                }
            })
            .create();
        mTerminalActionDialog = dialog;
        dialog.show();
        return true;
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
            if (!shouldShowInRecents())
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

    /**
     * Hook system menu to show the terminal action sheet instead.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        showTerminalActionSheet();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (handleTerminalAction(item.getItemId())) {
            return true;
        }
        return super.onContextItemSelected(item);
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
        return mExtraKeysView;
    }
    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys(int i) {
        return mTermuxTerminalExtraKeys;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView, int i) {
        mExtraKeysView = extraKeysView;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
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

    public boolean isImeVisibleForLayout() {
        return isImeVisible();
    }

    public boolean isWallpaperPassthroughEnabled() {
        return shouldUseWallpaperPassthroughMode();
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

    public void updateWindowBackgroundForCurrentSession() {
        if (getWindow() == null) {
            return;
        }
        if (shouldUseWallpaperPassthroughMode()) {
            Drawable managedWallpaperBackground = getManagedWallpaperWindowBackground();
            if (managedWallpaperBackground != null) {
                getWindow().getDecorView().setBackground(managedWallpaperBackground);
            } else {
                getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);
            }
            return;
        }
        clearManagedWallpaperWindowBackgroundCache();
        getWindow().getDecorView().setBackgroundColor(
            getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base)
        );
    }

    @Nullable
    private Drawable getManagedWallpaperWindowBackground() {
        if (!shouldUseManagedWallpaperBlurSource()) {
            return null;
        }
        File sourceFile = getManagedWallpaperExactFile();
        long lastModified = sourceFile.lastModified();
        long length = sourceFile.length();
        if (mManagedWallpaperWindowBackground != null &&
            mManagedWallpaperWindowBackgroundLastModified == lastModified &&
            mManagedWallpaperWindowBackgroundLength == length) {
            return mManagedWallpaperWindowBackground;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
        if (bitmap == null) {
            clearManagedWallpaperWindowBackgroundCache();
            return null;
        }
        mManagedWallpaperWindowBackground = new CenterCropBitmapDrawable(bitmap);
        mManagedWallpaperWindowBackgroundLastModified = lastModified;
        mManagedWallpaperWindowBackgroundLength = length;
        return mManagedWallpaperWindowBackground;
    }

    private void clearManagedWallpaperWindowBackgroundCache() {
        mManagedWallpaperWindowBackground = null;
        mManagedWallpaperWindowBackgroundLastModified = -1L;
        mManagedWallpaperWindowBackgroundLength = -1L;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || mIsInvalidState || !mIsVisible) {
            return;
        }
        mAccessoryBackdropDirty = true;
        scheduleAccessoryRenderSync("window:focus");
        restartAccessoryBlurHeartbeat();
        scheduleAccessoryBlurRecovery();
    }

    @Override
    public void reloadSuggestionBar(char inputChar) {
        if (!isSuggestionBarEnabled() || mSuggestionBarView == null || mTerminalView == null) {
            return;
        }
        resetAzGestureState(false, true);
        mSuggestionBarView.onTerminalInteraction();
        if (inputChar == getSuggestionBarSplitChar()) {
            mSuggestionBarExplicitSearchActive = true;
        }
        if (!mSuggestionBarExplicitSearchActive) {
            return;
        }
        String input = normalizeSuggestionBarInput(mTerminalView.getCurrentInput(inputChar));
        if (!input.isEmpty()) {
            mSuggestionBarView.reloadWithInput(input, mTerminalView);
            return;
        }
        if (mTerminalView.getCurrentInput() != null && !mTerminalView.getCurrentInput().trim().isEmpty()) {
            mSuggestionBarExplicitSearchActive = false;
        }
        if (mSuggestionBarView.isSearchSurfaceActive()) {
            mSuggestionBarView.reloadWithInput("", mTerminalView);
        }
    }

    @Override
    public void reloadSuggestionBar(boolean delete, boolean enter) {
        if (!isSuggestionBarEnabled() || mSuggestionBarView == null || mTerminalView == null) {
            return;
        }
        resetAzGestureState(false, true);
        mSuggestionBarView.onTerminalInteraction();
        if (enter) {
            mSuggestionBarExplicitSearchActive = false;
            if (mSuggestionBarView.isSearchSurfaceActive()) {
                mSuggestionBarView.reloadWithInput("", mTerminalView);
            }
            return;
        }
        if (!mSuggestionBarExplicitSearchActive && !mSuggestionBarView.isSearchSurfaceActive()) {
            return;
        }
        String input = normalizeSuggestionBarInput(mTerminalView.getCurrentInput());
        if (!input.isEmpty()) {
            mSuggestionBarView.reloadWithInput(input, mTerminalView);
            return;
        }
        mSuggestionBarExplicitSearchActive = false;
        if (mSuggestionBarView.isSearchSurfaceActive()) {
            mSuggestionBarView.reloadWithInput("", mTerminalView);
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
        intentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
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
                        if (mSuggestionBarView != null) {
                            mSuggestionBarView.invalidateShortcutCache(packageName);
                        }
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
        if (!isSuggestionBarEnabled() || mSuggestionBarView == null) {
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
        if (mTerminalView != null && mSuggestionBarExplicitSearchActive) {
            input = normalizeSuggestionBarInput(mTerminalView.getCurrentInput());
        }
        mSuggestionBarView.reloadWithInput(input, mTerminalView);
        updateAzOverflowAffordance();
    }

    private void addAccessoryKeyboardLayoutListener() {
        View content = findViewById(android.R.id.content);
        if (content == null || mAccessoryKeyboardLayoutListener != null) {
            return;
        }
        mLastImeVisible = isImeVisible();
        mAccessoryKeyboardLayoutListener = () -> {
            boolean imeVisible = isImeVisible();
            if (imeVisible != mLastImeVisible) {
                mLastImeVisible = imeVisible;
                onImeVisibilityChanged(imeVisible);
            }
        };
        content.getViewTreeObserver().addOnGlobalLayoutListener(mAccessoryKeyboardLayoutListener);
    }

    private void removeAccessoryKeyboardLayoutListener() {
        View content = findViewById(android.R.id.content);
        if (content == null || mAccessoryKeyboardLayoutListener == null) {
            return;
        }
        content.getViewTreeObserver().removeOnGlobalLayoutListener(mAccessoryKeyboardLayoutListener);
        mAccessoryKeyboardLayoutListener = null;
    }

    private void addAccessoryLayoutChangeListeners() {
        if (mAccessoryLayoutChangeListener != null) {
            return;
        }
        mAccessoryLayoutChangeListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int oldWidth = oldRight - oldLeft;
            int height = bottom - top;
            int oldHeight = oldBottom - oldTop;
            if (width == oldWidth && height == oldHeight) {
                return;
            }
            scheduleAccessoryRenderSync("accessory:layout");
        };
        int[] watchIds = {
            R.id.accessory_stack_container,
            R.id.apps_bar_viewpager,
            R.id.apps_bar_indicator_band,
            R.id.apps_bar_az_row,
            R.id.terminal_toolbar_view_pager
        };
        for (int watchId : watchIds) {
            View watchView = findViewById(watchId);
            if (watchView != null) {
                watchView.addOnLayoutChangeListener(mAccessoryLayoutChangeListener);
            }
        }
    }

    private void removeAccessoryLayoutChangeListeners() {
        if (mAccessoryLayoutChangeListener == null) {
            return;
        }
        int[] watchIds = {
            R.id.accessory_stack_container,
            R.id.apps_bar_viewpager,
            R.id.apps_bar_indicator_band,
            R.id.apps_bar_az_row,
            R.id.terminal_toolbar_view_pager
        };
        for (int watchId : watchIds) {
            View watchView = findViewById(watchId);
            if (watchView != null) {
                watchView.removeOnLayoutChangeListener(mAccessoryLayoutChangeListener);
            }
        }
        mAccessoryLayoutChangeListener = null;
    }

    private boolean isImeVisible() {
        View content = findViewById(android.R.id.content);
        if (content == null) {
            return false;
        }
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
        return insets != null && insets.isVisible(Type.ime());
    }

    private void onImeVisibilityChanged(boolean visible) {
        if (!visible && !mAzGestureActive) {
            mSuggestionBarInteractionActive = false;
            if (mSuggestionBarView != null) {
                mSuggestionBarView.clearAzPreview();
            }
        }
        if (mTermuxTerminalSessionActivityClient != null) {
            mTermuxTerminalSessionActivityClient.onImeVisibilityChanged(visible);
        }
        applyAccessoryGeometryIfNeeded(true, visible ? "ime:open" : "ime:close");
        scheduleAccessoryRenderSync(visible ? "ime:open" : "ime:close");
        restartAccessoryBlurHeartbeat();
        scheduleAccessoryBlurRecovery();
    }

    private void scheduleAccessoryRenderSync(@NonNull String reason) {
        if (reason.contains("wallpaper") || reason.contains("style") || reason.contains("blur")) {
            mAccessoryBackdropDirty = true;
        }
        mPendingAccessoryRenderReason = reason;
        if (mAccessoryRenderSyncPending) {
            return;
        }
        mAccessoryRenderSyncPending = true;
        mAccessoryRenderHandler.post(mAccessoryRenderSyncRunnable);
    }

    private void syncTerminalOverlayBottomInsetToAccessoryHeight() {
        // Terminal glass layers now live inside terminal_surface_host, which already ends above the
        // accessory stack. There is no separate terminal inset layer left to sync here.
    }

    private void enforceAccessoryFxInvariants() {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer == null || accessoryContainer.getVisibility() != View.VISIBLE) {
            resetAzOverflowAffordanceState();
            return;
        }
        if (mSuggestionBarView == null) {
            resetAzOverflowAffordanceState();
            return;
        }
        boolean hasOverflow = mSuggestionBarView.hasAzOverflowPages() || mSuggestionBarView.hasPinnedOverflowPages();
        if (!hasOverflow && !mAzGestureActive && !mSuggestionBarInteractionActive) {
            resetAzOverflowAffordanceState();
            return;
        }
        updateAzOverflowAffordance();
    }

    private void logAccessoryRenderSnapshot(@NonNull String reason) {
        if (!ACCESSORY_RENDER_TRACE) {
            return;
        }
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        View azFxOverlay = findViewById(R.id.apps_bar_az_fx_overlay);
        View azFxUnderlay = findViewById(R.id.apps_bar_az_fx_underlay);
        View appsBar = findViewById(R.id.apps_bar_viewpager);
        long now = SystemClock.uptimeMillis();
        long delta = mLastAccessoryRenderSyncUptimeMs == 0L ? 0L : now - mLastAccessoryRenderSyncUptimeMs;
        mLastAccessoryRenderSyncUptimeMs = now;
        Logger.logVerbose(
            LOG_TAG,
            "AccessorySync reason=" + reason +
                " dt=" + delta +
                " ime=" + mLastImeVisible +
                " accessoryVis=" + (accessoryContainer != null ? accessoryContainer.getVisibility() : -1) +
                " appsBarVis=" + (appsBar != null ? appsBar.getVisibility() : -1) +
                " fxU=" + (azFxUnderlay != null ? azFxUnderlay.getVisibility() : -1) +
                " fxO=" + (azFxOverlay != null ? azFxOverlay.getVisibility() : -1)
        );
    }

    private void scheduleSuggestionBarPackageRefresh(boolean immediate, boolean forceCatalogRefresh) {
        if (!isSuggestionBarEnabled()) {
            mPackageRefreshForceCatalogReload = false;
            mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
            return;
        }
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

    private void scheduleLauncherCatalogWarmup() {
        mAzGestureHandler.removeCallbacks(mLauncherCatalogWarmRunnable);
        if (mIsVisible && isSuggestionBarEnabled() && mSuggestionBarView != null) {
            mAzGestureHandler.postDelayed(mLauncherCatalogWarmRunnable, LAUNCHER_CATALOG_WARM_DELAY_MS);
        }
    }

    private void runLauncherCatalogWarmup() {
        if (!mIsVisible || !isSuggestionBarEnabled() || mSuggestionBarView == null) {
            return;
        }
        mSuggestionBarView.reloadAllApps();
        mSuggestionBarView.reload();
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
                Intent.ACTION_PACKAGE_CHANGED.equals(action) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
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
            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }
        setMargins();
        updateAppLauncherBarHeight();
        applySuggestionBarPreferences();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.resetTransientVisualState();
        }
        applySuggestionBarInputChar();
        applyAccessoryGeometryIfNeeded(true, "reloadActivityStyling");
        applySeamlessStatusBackgroundModeIfNeeded();
        applyTerminalSurfaceAppearance();
        syncTerminalWallpaperRenderingMode();
        updateWindowBackgroundForCurrentSession();
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

    private static final class CenterCropBitmapDrawable extends Drawable {
        @NonNull private final Bitmap mBitmap;
        @NonNull private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        @NonNull private final RectF mDestinationRect = new RectF();

        CenterCropBitmapDrawable(@NonNull Bitmap bitmap) {
            mBitmap = bitmap;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            int boundsWidth = Math.max(1, bounds.width());
            int boundsHeight = Math.max(1, bounds.height());
            int bitmapWidth = Math.max(1, mBitmap.getWidth());
            int bitmapHeight = Math.max(1, mBitmap.getHeight());
            float scale = Math.max((float) boundsWidth / bitmapWidth, (float) boundsHeight / bitmapHeight);
            float drawWidth = bitmapWidth * scale;
            float drawHeight = bitmapHeight * scale;
            float left = bounds.left + ((boundsWidth - drawWidth) / 2f);
            float top = bounds.top + ((boundsHeight - drawHeight) / 2f);
            mDestinationRect.set(left, top, left + drawWidth, top + drawHeight);
            canvas.drawBitmap(mBitmap, null, mDestinationRect, mPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

}
