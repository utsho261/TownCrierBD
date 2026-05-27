package com.example.towncrierbd.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.towncrierbd.utils.AppStrings;
import com.example.towncrierbd.utils.CategoryConfig;
import com.example.towncrierbd.utils.CategoryConfig.HawkerCategory;
import com.example.towncrierbd.utils.CategoryConfig.SubGroup;
import com.example.towncrierbd.utils.LanguageManager;

import java.util.*;

/**
 * Category selection screen — fully bilingual.
 * All UI text, category names, subcategory group names and item names
 * are displayed in the current app language (EN / BN).
 *
 * Internal storage always uses English keys — Bangla is display-only.
 */
public class HawkerCategoryActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORIES    = "hawker_categories";
    public static final String EXTRA_SUBCATEGORIES = "hawker_subcategories";
    public static final String EXTRA_OTHERS_NAME   = "hawker_others_name";

    private final Set<String>  selectedCategories    = new LinkedHashSet<>();
    private final Set<String>  selectedSubcategories = new LinkedHashSet<>();
    private       String       othersCustomName       = "";
    private final List<String> othersCustomSubs       = new ArrayList<>();

    private LinearLayout llCategoryList;
    private TextView     tvDoneBtn;

    private final Map<String, View>     subPanels   = new LinkedHashMap<>();
    private final Map<String, TextView> catCheckMap = new LinkedHashMap<>();
    private final Map<String, View>     catRowMap   = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent incoming = getIntent();
        if (incoming != null) {
            ArrayList<String> preCats = incoming.getStringArrayListExtra(EXTRA_CATEGORIES);
            ArrayList<String> preSubs = incoming.getStringArrayListExtra(EXTRA_SUBCATEGORIES);
            String preOthers = incoming.getStringExtra(EXTRA_OTHERS_NAME);

            if (preCats != null)  selectedCategories.addAll(preCats);
            if (preSubs != null)  selectedSubcategories.addAll(preSubs);
            if (preOthers != null && !preOthers.isEmpty()) othersCustomName = preOthers;
        }

        buildUI();
    }

    private void buildUI() {
        AppStrings s = AppStrings.get(this);

        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(0xFFF4F7FF);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(100));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(roundedBg(0xFF1976F3, dp(18)));
        header.setPadding(dp(18), dp(20), dp(18), dp(20));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(s.catScreenTitle());
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, Typeface.BOLD);
        header.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(s.catScreenSubtitle());
        tvSub.setTextColor(0xFFD6E7FF);
        tvSub.setTextSize(13);
        tvSub.setPadding(0, dp(4), 0, 0);
        header.addView(tvSub);
        container.addView(header);

        // Language toggle inside the screen
        TextView tvLang = new TextView(this);
        tvLang.setText(LanguageManager.getToggleLabel(this));
        tvLang.setTextSize(12);
        tvLang.setTextColor(0xFF1976F3);
        tvLang.setTextSize(Typeface.BOLD);
        tvLang.setPadding(dp(4), dp(10), dp(4), dp(2));
        tvLang.setClickable(true);
        tvLang.setFocusable(true);
        tvLang.setOnClickListener(v -> {
            LanguageManager.toggle(this);
            // Rebuild UI with new language
            finish();
            startActivity(getIntent());
            overridePendingTransition(0, 0);
        });
        container.addView(tvLang);

        TextView tvHint = new TextView(this);
        tvHint.setText(s.catScreenHint());
        tvHint.setTextColor(0xFF6B7280);
        tvHint.setTextSize(12);
        tvHint.setPadding(dp(2), dp(4), dp(2), dp(8));
        container.addView(tvHint);

        llCategoryList = new LinearLayout(this);
        llCategoryList.setOrientation(LinearLayout.VERTICAL);
        container.addView(llCategoryList);

        for (HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES) {
            addCategoryRow(cat);
        }

        restorePreSelectedUI();
        root.addView(container);

        FrameLayout frame = new FrameLayout(this);
        frame.addView(root);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFFFFFFFF);
        bottomBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        bottomBar.setElevation(dp(8));

        tvDoneBtn = new TextView(this);
        tvDoneBtn.setTextColor(0xFFFFFFFF);
        tvDoneBtn.setTextSize(16);
        tvDoneBtn.setTypeface(null, Typeface.BOLD);
        tvDoneBtn.setGravity(Gravity.CENTER);
        tvDoneBtn.setPadding(dp(24), dp(14), dp(24), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvDoneBtn.setLayoutParams(lp);
        bottomBar.addView(tvDoneBtn);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        frame.addView(bottomBar, barLp);

        setContentView(frame);

        refreshDoneButton();
        tvDoneBtn.setOnClickListener(v -> onDone());
    }

    private void setTextStyle(TextView tv, int style) {
        tv.setTypeface(null, style);
    }

    private void restorePreSelectedUI() {
        for (HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES) {
            if (!selectedCategories.contains(cat.name)) continue;

            View row = catRowMap.get(cat.name);
            TextView tvCheck = catCheckMap.get(cat.name);
            View subPanel = subPanels.get(cat.name);

            if (row != null) row.setBackground(roundedBg(0xFFEFF6FF, dp(14)));
            if (tvCheck != null) tvCheck.setVisibility(View.VISIBLE);
            if (subPanel != null) {
                boolean hasSubs = !cat.subGroups.isEmpty() || "Others".equals(cat.name);
                subPanel.setVisibility(hasSubs ? View.VISIBLE : View.GONE);
            }

            if ("Others".equals(cat.name) && !othersCustomName.isEmpty() && etOthersName != null) {
                etOthersName.setText(othersCustomName);
            }
        }
        recheckSelectedInAll();
    }

    private void recheckSelectedInAll() {
        if (llCategoryList == null) return;
        for (int i = 0; i < llCategoryList.getChildCount(); i++) {
            View child = llCategoryList.getChildAt(i);
            if (child instanceof LinearLayout) {
                recheckSelected((LinearLayout) child);
            }
        }
    }

    private void addCategoryRow(HawkerCategory cat) {
        AppStrings s = AppStrings.get(this);
        boolean isBn = !LanguageManager.isEnglish(this);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wLp.setMargins(0, dp(6), 0, 0);
        wrapper.setLayoutParams(wLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(roundedBg(0xFFFFFFFF, dp(14)));
        row.setElevation(dp(2));

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(cat.emoji + "  ");
        tvEmoji.setTextSize(20);

        TextView tvName = new TextView(this);
        // ✅ Show category name in current language
        tvName.setText(isBn ? cat.nameBn : cat.name);
        tvName.setTextSize(15);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(0xFF1F2937);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(nameLp);

        int totalItems = 0;
        for (SubGroup g : cat.subGroups) totalItems += g.items.size();
        TextView tvCount = new TextView(this);
        if (totalItems > 0) {
            tvCount.setText(s.addStep1Items(totalItems));
            tvCount.setTextSize(11);
            tvCount.setTextColor(0xFF9CA3AF);
            tvCount.setPadding(0, 0, dp(8), 0);
        }

        TextView tvCheck = new TextView(this);
        tvCheck.setText("✓");
        tvCheck.setTextSize(14);
        tvCheck.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCheck.setTextColor(0xFFFFFFFF);
        tvCheck.setPadding(dp(8), dp(4), dp(8), dp(4));
        tvCheck.setBackground(roundedBg(0xFF1976F3, dp(10)));
        tvCheck.setVisibility(View.GONE);

        TextView tvChevron = new TextView(this);
        tvChevron.setText("  ▾");
        tvChevron.setTextSize(14);
        tvChevron.setTextColor(0xFF9CA3AF);

        row.addView(tvEmoji);
        row.addView(tvName);
        row.addView(tvCount);
        row.addView(tvCheck);
        row.addView(tvChevron);

        LinearLayout subPanel = new LinearLayout(this);
        subPanel.setOrientation(LinearLayout.VERTICAL);
        subPanel.setVisibility(View.GONE);
        subPanel.setBackgroundColor(0xFFF9FAFB);
        subPanel.setPadding(dp(14), dp(8), dp(14), dp(12));

        if ("Others".equals(cat.name)) {
            buildOthersPanel(subPanel);
        } else {
            buildSubPanel(subPanel, cat);
        }

        subPanels.put(cat.name, subPanel);
        catCheckMap.put(cat.name, tvCheck);
        catRowMap.put(cat.name, row);

        wrapper.addView(row);
        wrapper.addView(subPanel);
        llCategoryList.addView(wrapper);

        row.setOnClickListener(v -> {
            boolean nowSelected = selectedCategories.contains(cat.name);
            if (!nowSelected) {
                selectedCategories.add(cat.name);
                row.setBackground(roundedBg(0xFFEFF6FF, dp(14)));
                tvCheck.setVisibility(View.VISIBLE);
                tvChevron.setText("  ▴");
                boolean hasSubs = !cat.subGroups.isEmpty() || "Others".equals(cat.name);
                subPanel.setVisibility(hasSubs ? View.VISIBLE : View.GONE);
            } else {
                if (subPanel.getVisibility() == View.VISIBLE) {
                    subPanel.setVisibility(View.GONE);
                    tvChevron.setText("  ▾");
                } else {
                    boolean hasSubs = !cat.subGroups.isEmpty() || "Others".equals(cat.name);
                    subPanel.setVisibility(hasSubs ? View.VISIBLE : View.GONE);
                    tvChevron.setText("  ▴");
                }
            }
            refreshDoneButton();
        });

        row.setOnLongClickListener(v -> {
            if (selectedCategories.contains(cat.name)) {
                selectedCategories.remove(cat.name);
                removeSubcategoriesOf(cat);
                row.setBackground(roundedBg(0xFFFFFFFF, dp(14)));
                tvCheck.setVisibility(View.GONE);
                tvChevron.setText("  ▾");
                subPanel.setVisibility(View.GONE);
                refreshDoneButton();
                Toast.makeText(this, AppStrings.get(this).catDeselected(isBn ? cat.nameBn : cat.name), Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void buildSubPanel(LinearLayout panel, HawkerCategory cat) {
        AppStrings s = AppStrings.get(this);
        boolean isBn = !LanguageManager.isEnglish(this);

        TextView tvSelectAll = new TextView(this);
        tvSelectAll.setText(s.catSelectAll(isBn ? cat.nameBn : cat.name));
        tvSelectAll.setTextSize(12);
        tvSelectAll.setTextColor(0xFF1976F3);
        tvSelectAll.setPadding(0, dp(6), 0, dp(8));
        tvSelectAll.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSelectAll.setOnClickListener(v -> {
            // Always store English keys internally
            for (SubGroup g : cat.subGroups) {
                selectedSubcategories.addAll(g.items);
            }
            recheckSelected(panel);
            refreshDoneButton();
        });
        panel.addView(tvSelectAll);

        for (SubGroup group : cat.subGroups) {
            TextView tvGroup = new TextView(this);
            // ✅ Show group name in current language
            tvGroup.setText("▸ " + (isBn ? group.groupNameBn : group.groupName));
            tvGroup.setTextSize(12);
            tvGroup.setTypeface(null, android.graphics.Typeface.BOLD);
            tvGroup.setTextColor(0xFF6B7280);
            tvGroup.setPadding(0, dp(10), 0, dp(4));
            panel.addView(tvGroup);

            // ✅ Display items in current language but store English keys
            List<String> displayItems = isBn ? group.itemsBn : group.items;
            for (int i = 0; i < displayItems.size(); i += 2) {
                LinearLayout itemRow = new LinearLayout(this);
                itemRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.setMargins(0, dp(1), 0, dp(1));
                itemRow.setLayoutParams(rlp);

                // English key for storage, display text for UI
                String engKey0 = group.items.get(i);
                String dispTxt0 = displayItems.get(i);
                addCheckItem(itemRow, engKey0, dispTxt0);

                if (i + 1 < displayItems.size()) {
                    String engKey1 = group.items.get(i + 1);
                    String dispTxt1 = displayItems.get(i + 1);
                    addCheckItem(itemRow, engKey1, dispTxt1);
                }
                panel.addView(itemRow);
            }
        }
    }

    private EditText etOthersName;
    private LinearLayout llOthersSubs;
    private EditText etOthersSubInput;

    private void buildOthersPanel(LinearLayout panel) {
        AppStrings s = AppStrings.get(this);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(s.catOthersLabel());
        tvLabel.setTextSize(12);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setTextColor(0xFF6B7280);
        tvLabel.setPadding(0, dp(6), 0, dp(4));
        panel.addView(tvLabel);

        etOthersName = new EditText(this);
        etOthersName.setHint(s.catOthersHint());
        etOthersName.setBackground(editBg());
        etOthersName.setPadding(dp(12), dp(10), dp(12), dp(10));
        etOthersName.setTextSize(14);
        if (!othersCustomName.isEmpty()) etOthersName.setText(othersCustomName);
        panel.addView(etOthersName);

        etOthersName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s2, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s2) {}
            @Override public void onTextChanged(CharSequence s2, int st, int b, int c) {
                othersCustomName = s2.toString().trim();
            }
        });

        TextView tvSubLabel = new TextView(this);
        tvSubLabel.setText(s.catOthersSubLabel());
        tvSubLabel.setTextSize(12);
        tvSubLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSubLabel.setTextColor(0xFF6B7280);
        tvSubLabel.setPadding(0, dp(12), 0, dp(4));
        panel.addView(tvSubLabel);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        etOthersSubInput = new EditText(this);
        etOthersSubInput.setHint(s.catOthersItemHint());
        etOthersSubInput.setBackground(editBg());
        etOthersSubInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        etOthersSubInput.setTextSize(14);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        etOthersSubInput.setLayoutParams(etLp);

        TextView btnAdd = new TextView(this);
        btnAdd.setText(s.catOthersAddBtn());
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setTextSize(13);
        btnAdd.setTypeface(null, android.graphics.Typeface.BOLD);
        btnAdd.setPadding(dp(14), dp(10), dp(14), dp(10));
        btnAdd.setBackground(roundedBg(0xFF1976F3, dp(10)));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(dp(8), 0, 0, 0);
        btnAdd.setLayoutParams(btnLp);

        inputRow.addView(etOthersSubInput);
        inputRow.addView(btnAdd);
        panel.addView(inputRow);

        llOthersSubs = new LinearLayout(this);
        llOthersSubs.setOrientation(LinearLayout.VERTICAL);
        panel.addView(llOthersSubs);

        for (String existingSub : othersCustomSubs) {
            addOthersSubChip(llOthersSubs, existingSub);
        }

        btnAdd.setOnClickListener(v -> {
            String text = etOthersSubInput.getText().toString().trim();
            if (text.isEmpty()) return;
            othersCustomSubs.add(text);
            selectedSubcategories.add(text);
            addOthersSubChip(llOthersSubs, text);
            etOthersSubInput.setText("");
            refreshDoneButton();
        });
    }

    private void addOthersSubChip(LinearLayout container, String text) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(roundedBg(0xFFEFF6FF, dp(8)));
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.setMargins(0, dp(4), 0, 0);
        chip.setLayoutParams(chipLp);
        chip.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView tvText = new TextView(this);
        tvText.setText("• " + text);
        tvText.setTextSize(13);
        tvText.setTextColor(0xFF1F2937);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvText.setLayoutParams(tLp);

        TextView tvRemove = new TextView(this);
        tvRemove.setText("✕");
        tvRemove.setTextSize(13);
        tvRemove.setTextColor(0xFFEF4444);
        tvRemove.setPadding(dp(8), 0, 0, 0);

        chip.addView(tvText);
        chip.addView(tvRemove);
        container.addView(chip);

        tvRemove.setOnClickListener(v -> {
            othersCustomSubs.remove(text);
            selectedSubcategories.remove(text);
            container.removeView(chip);
            refreshDoneButton();
        });
    }

    /**
     * @param engKey     English key stored in Firebase
     * @param displayText Text shown to user (may be Bangla)
     */
    private void addCheckItem(LinearLayout parent, String engKey, String displayText) {
        CheckBox cb = new CheckBox(this);
        cb.setText(displayText);
        cb.setTextSize(13);
        cb.setTextColor(0xFF374151);
        cb.setChecked(selectedSubcategories.contains(engKey));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.setMargins(dp(2), dp(1), dp(2), dp(1));
        cb.setLayoutParams(clp);
        cb.setTag(engKey); // Always store English key as tag

        cb.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) selectedSubcategories.add(engKey);
            else selectedSubcategories.remove(engKey);
            refreshDoneButton();
        });

        parent.addView(cb);
    }

    private void removeSubcategoriesOf(HawkerCategory cat) {
        if ("Others".equals(cat.name)) {
            for (String s : othersCustomSubs) selectedSubcategories.remove(s);
            othersCustomSubs.clear();
            othersCustomName = "";
            if (etOthersName != null) etOthersName.setText("");
            if (llOthersSubs != null) llOthersSubs.removeAllViews();
            return;
        }
        for (SubGroup g : cat.subGroups) {
            selectedSubcategories.removeAll(g.items);
        }
    }

    private void recheckSelected(LinearLayout parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox cb = (CheckBox) child;
                String tag = (String) cb.getTag(); // English key
                if (tag != null) cb.setChecked(selectedSubcategories.contains(tag));
            } else if (child instanceof LinearLayout) {
                recheckSelected((LinearLayout) child);
            }
        }
    }

    private void refreshDoneButton() {
        AppStrings s = AppStrings.get(this);
        boolean hasCategory = !selectedCategories.isEmpty();
        tvDoneBtn.setBackground(roundedBg(hasCategory ? 0xFF1976F3 : 0xFFB0BEC5, dp(14)));
        int count = selectedCategories.size();
        int subCount = selectedSubcategories.size();
        if (count == 0) {
            tvDoneBtn.setText(s.catSelectAtLeast());
        } else {
            tvDoneBtn.setText(s.catDoneBtn(count, subCount));
        }
    }

    private void onDone() {
        AppStrings s = AppStrings.get(this);
        if (selectedCategories.isEmpty()) {
            Toast.makeText(this, s.catSelectAtLeast(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedCategories.contains("Others") && othersCustomName.isEmpty()) {
            Toast.makeText(this, s.catOthersNameRequired(), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        result.putStringArrayListExtra(EXTRA_CATEGORIES,    new ArrayList<>(selectedCategories));
        result.putStringArrayListExtra(EXTRA_SUBCATEGORIES, new ArrayList<>(selectedSubcategories));
        result.putExtra(EXTRA_OTHERS_NAME, othersCustomName);
        setResult(RESULT_OK, result);
        finish();
    }

    private android.graphics.drawable.GradientDrawable roundedBg(int color, int radius) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        return gd;
    }

    private android.graphics.drawable.GradientDrawable editBg() {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(0xFFFFFFFF);
        gd.setStroke(dp(1), 0xFFD1D5DB);
        gd.setCornerRadius(dp(10));
        return gd;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}