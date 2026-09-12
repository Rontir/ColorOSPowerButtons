package com.heytap.wallet;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Insets;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Material You configuration screen for the single double-power action. */
public final class SettingsActivity extends Activity {
    private final List<AppEntry> apps = new ArrayList<>();

    private ListView appList;
    private AppAdapter adapter;
    private ImageView selectedIcon;
    private TextView selectedName;
    private TextView selectedPackage;
    private TextView statusBadge;
    private TextView statusDescription;
    private View setupCard;
    private EditText searchApps;
    private Button testButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LaunchTargetStore.initializeDefault(this);
        setContentView(R.layout.activity_settings);
        configureSystemBars();
        bindViews();
        configureList();
        configureActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AccessibilityStateRepair.restoreIfAuthorized(this, "settings resume");
        refreshScreen();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(getColor(R.color.background));
        getWindow().setNavigationBarColor(getColor(R.color.background));
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            int lightFlags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(0, lightFlags);
        }
    }

    private void bindViews() {
        appList = findViewById(R.id.app_list);
        View header = getLayoutInflater().inflate(R.layout.header_settings, appList, false);
        appList.addHeaderView(header, null, false);

        selectedIcon = header.findViewById(R.id.selected_icon);
        selectedName = header.findViewById(R.id.selected_name);
        selectedPackage = header.findViewById(R.id.selected_package);
        statusBadge = header.findViewById(R.id.status_badge);
        statusDescription = header.findViewById(R.id.status_description);
        setupCard = header.findViewById(R.id.setup_card);
        searchApps = header.findViewById(R.id.search_apps);
        testButton = header.findViewById(R.id.test_button);

        appList.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom + dp(24));
            return insets;
        });
        appList.requestApplyInsets();
    }

    private void configureList() {
        adapter = new AppAdapter();
        appList.setAdapter(adapter);
        searchApps.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void configureActions() {
        testButton.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            if (!TargetLauncher.launchTarget(this)) {
                Toast.makeText(this, R.string.target_launch_failed, Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.accessibility_button).setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (RuntimeException exception) {
                Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshScreen() {
        discoverApps();
        String selected = LaunchTargetStore.getPackage(this);
        adapter.setApps(apps, selected);
        adapter.filter(searchApps.getText() == null ? "" : searchApps.getText().toString());
        renderSelectedTarget();
        renderStatus();
    }

    private void discoverApps() {
        apps.clear();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> matches = getPackageManager().queryIntentActivities(
                launcherIntent, PackageManager.MATCH_ALL);

        Map<String, ResolveInfo> byPackage = new LinkedHashMap<>();
        for (ResolveInfo match : matches) {
            if (match.activityInfo == null
                    || !match.activityInfo.exported
                    || !match.activityInfo.enabled
                    || getPackageName().equals(match.activityInfo.packageName)) {
                continue;
            }
            byPackage.putIfAbsent(match.activityInfo.packageName, match);
        }

        for (Map.Entry<String, ResolveInfo> item : byPackage.entrySet()) {
            CharSequence labelValue = item.getValue().loadLabel(getPackageManager());
            String label = labelValue == null ? item.getKey() : labelValue.toString();
            apps.add(new AppEntry(item.getKey(), label, item.getValue()));
        }

        Collator collator = Collator.getInstance();
        Collections.sort(apps, (left, right) -> {
            int labelOrder = collator.compare(left.label, right.label);
            return labelOrder != 0 ? labelOrder : left.packageName.compareTo(right.packageName);
        });
    }

    private void renderSelectedTarget() {
        String packageName = LaunchTargetStore.getPackage(this);
        String label = LaunchTargetStore.getLabel(this);
        boolean available = packageName != null
                && TargetLauncher.isPackageLaunchable(this, packageName);

        if (packageName == null) {
            selectedIcon.setImageResource(R.drawable.ic_power_launcher);
            selectedName.setText(R.string.no_target_selected);
            selectedPackage.setText("");
        } else {
            selectedName.setText(available
                    ? label
                    : getString(R.string.target_unavailable, label));
            selectedPackage.setText(packageName);
            selectedIcon.setImageDrawable(loadApplicationIcon(packageName));
        }

        testButton.setEnabled(available);
        testButton.setAlpha(available ? 1f : 0.45f);
    }

    private void renderStatus() {
        boolean redirectEnabled = isRedirectEnabled();
        if (redirectEnabled) {
            statusBadge.setText(R.string.status_active);
            statusBadge.setBackgroundResource(R.drawable.bg_badge_active);
            statusBadge.setTextColor(getColor(R.color.on_primary_container));
            statusDescription.setText(R.string.status_active_description);
            setupCard.setVisibility(View.GONE);
        } else {
            statusBadge.setText(R.string.status_setup);
            statusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            statusBadge.setTextColor(getColor(R.color.warning_text));
            statusDescription.setText(R.string.status_setup_description);
            setupCard.setVisibility(View.VISIBLE);
        }
    }

    private boolean isRedirectEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        ComponentName target = new ComponentName(this, WalletRedirectAccessibilityService.class);
        String flat = target.flattenToString();
        String shortFlat = target.flattenToShortString();
        for (String item : enabled.split(":")) {
            if (flat.equals(item) || shortFlat.equals(item)) {
                return true;
            }
        }
        return false;
    }

    private void selectApp(AppEntry app, View source) {
        LaunchTargetStore.setTarget(this, app.packageName, app.label);
        source.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        adapter.setSelectedPackage(app.packageName);
        renderSelectedTarget();
        Toast.makeText(
                this,
                getString(R.string.target_saved, app.label),
                Toast.LENGTH_SHORT).show();
    }

    private Drawable loadApplicationIcon(String packageName) {
        try {
            return getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return getDrawable(R.drawable.ic_power_launcher);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AppEntry {
        final String packageName;
        final String label;
        final ResolveInfo resolveInfo;

        AppEntry(String packageName, String label, ResolveInfo resolveInfo) {
            this.packageName = packageName;
            this.label = label;
            this.resolveInfo = resolveInfo;
        }
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<AppEntry> allApps = new ArrayList<>();
        private final List<AppEntry> visibleApps = new ArrayList<>();
        private final LruCache<String, Drawable> icons = new LruCache<>(48);
        private String selectedPackageName;
        private String query = "";

        void setApps(List<AppEntry> source, String selectedPackage) {
            allApps.clear();
            allApps.addAll(source);
            selectedPackageName = selectedPackage;
        }

        void setSelectedPackage(String packageName) {
            selectedPackageName = packageName;
            notifyDataSetChanged();
        }

        void filter(String newQuery) {
            query = newQuery == null ? "" : newQuery.trim().toLowerCase(Locale.ROOT);
            visibleApps.clear();
            for (AppEntry app : allApps) {
                if (query.isEmpty()
                        || app.label.toLowerCase(Locale.ROOT).contains(query)
                        || app.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                    visibleApps.add(app);
                }
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return visibleApps.size(); }
        @Override public AppEntry getItem(int position) { return visibleApps.get(position); }
        @Override public long getItemId(int position) { return getItem(position).packageName.hashCode(); }
        @Override public boolean hasStableIds() { return true; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            ViewHolder holder;
            if (row == null) {
                row = LayoutInflater.from(SettingsActivity.this)
                        .inflate(R.layout.item_app, parent, false);
                holder = new ViewHolder(row);
                row.setTag(holder);
            } else {
                holder = (ViewHolder) row.getTag();
            }

            AppEntry app = getItem(position);
            boolean selected = app.packageName.equals(selectedPackageName);
            Drawable icon = icons.get(app.packageName);
            if (icon == null) {
                try {
                    icon = app.resolveInfo.loadIcon(getPackageManager());
                } catch (RuntimeException ignored) {
                    icon = getDrawable(R.drawable.ic_power_launcher);
                }
                icons.put(app.packageName, icon);
            }

            holder.icon.setImageDrawable(icon);
            holder.name.setText(app.label);
            holder.packageName.setText(app.packageName);
            holder.badge.setVisibility(selected ? View.VISIBLE : View.GONE);
            holder.radio.setChecked(selected);
            holder.card.setSelected(selected);
            holder.card.setOnClickListener(view -> selectApp(app, view));
            return row;
        }
    }

    private static final class ViewHolder {
        final View card;
        final ImageView icon;
        final TextView name;
        final TextView packageName;
        final TextView badge;
        final RadioButton radio;

        ViewHolder(View row) {
            card = row.findViewById(R.id.app_card);
            icon = row.findViewById(R.id.app_icon);
            name = row.findViewById(R.id.app_name);
            packageName = row.findViewById(R.id.app_package);
            badge = row.findViewById(R.id.selected_badge);
            radio = row.findViewById(R.id.app_radio);
        }
    }
}
