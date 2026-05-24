package com.example.towncrierbd.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.towncrierbd.R;
import com.example.towncrierbd.utils.CategoryConfig;
import com.example.towncrierbd.utils.CategoryConfig.HawkerCategory;
import com.example.towncrierbd.utils.CategoryConfig.SubGroup;

import java.util.*;

/**
 * Announcer signup এর পরে এই screen আসে।
 *
 * User পারবে:
 *  • Multiple category select করতে
 *  • প্রতিটা category-র sub-items multi-select করতে (grouped checkbox list)
 *  • "Others" category select করলে custom category name type করতে
 *    এবং custom sub-items add করতে
 *
 * Result Intent extras:
 *   EXTRA_CATEGORIES      → ArrayList<String>  (selected category names)
 *   EXTRA_SUBCATEGORIES   → ArrayList<String>  (selected sub-item names)
 *   EXTRA_OTHERS_NAME     → String             (custom category name if Others selected)
 */
public class HawkerCategoryActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORIES    = "hawker_categories";
    public static final String EXTRA_SUBCATEGORIES = "hawker_subcategories";
    public static final String EXTRA_OTHERS_NAME   = "hawker_others_name";

    // Selected state
    private final Set<String>  selectedCategories    = new LinkedHashSet<>();
    private final Set<String>  selectedSubcategories = new LinkedHashSet<>();
    private       String       othersCustomName       = "";

    // Custom sub-items added under "Others"
    private final List<String> othersCustomSubs = new ArrayList<>();

    // UI
    private LinearLayout llCategoryList;
    private TextView     tvDoneBtn;

    // Map: categoryName → its expandable sub-panel
    private final Map<String, View> subPanels = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Build layout programmatically (no separate XML needed)
        buildUI();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Build full UI programmatically
    // ══════════════════════════════════════════════════════════════════════

    private void buildUI() {
        // Root scroll
        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(0xFFF4F7FF);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(80));

        // ── Header ─────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(0xFF1976F3);
        header.setPadding(dp(18), dp(20), dp(18), dp(20));
        int r = dp(18);
        header.setBackground(roundedBg(0xFF1976F3, r));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("What do you sell?");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText("Select your categories & items — buyers will find you nearby");
        tvSub.setTextColor(0xFFD6E7FF);
        tvSub.setTextSize(13);
        tvSub.setPadding(0, dp(4), 0, 0);
        header.addView(tvSub);

        container.addView(header);

        // ── Hint ────────────────────────────────────────────────────────
        TextView tvHint = new TextView(this);
        tvHint.setText("Tap a category to select it. Tap again to expand and choose items.");
        tvHint.setTextColor(0xFF6B7280);
        tvHint.setTextSize(12);
        tvHint.setPadding(dp(2), dp(12), dp(2), dp(8));
        container.addView(tvHint);

        // ── Category list ───────────────────────────────────────────────
        llCategoryList = new LinearLayout(this);
        llCategoryList.setOrientation(LinearLayout.VERTICAL);
        container.addView(llCategoryList);

        for (HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES) {
            addCategoryRow(cat);
        }

        root.addView(container);
        setContentView(root);

        // ── Sticky Done button (overlaid) ────────────────────────────────
        FrameLayout frame = new FrameLayout(this);
        frame.addView(root);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFFFFFFFF);
        bottomBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        bottomBar.setElevation(dp(8));

        tvDoneBtn = new TextView(this);
        tvDoneBtn.setText("Done →");
        tvDoneBtn.setTextColor(0xFFFFFFFF);
        tvDoneBtn.setTextSize(16);
        tvDoneBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        tvDoneBtn.setGravity(Gravity.CENTER);
        tvDoneBtn.setPadding(dp(24), dp(14), dp(24), dp(14));
        tvDoneBtn.setBackground(roundedBg(0xFF1976F3, dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvDoneBtn.setLayoutParams(lp);
        bottomBar.addView(tvDoneBtn);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        frame.addView(bottomBar, barLp);

        setContentView(frame);

        tvDoneBtn.setOnClickListener(v -> onDone());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Add one category row + collapsible sub-panel
    // ══════════════════════════════════════════════════════════════════════

    private void addCategoryRow(HawkerCategory cat) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wLp.setMargins(0, dp(6), 0, 0);
        wrapper.setLayoutParams(wLp);

        // ── Category header row ────────────────────────────────────────
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(roundedBg(0xFFFFFFFF, dp(14)));
        row.setElevation(dp(2));

        // Emoji + name
        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(cat.emoji + "  ");
        tvEmoji.setTextSize(20);

        TextView tvName = new TextView(this);
        tvName.setText(cat.name);
        tvName.setTextSize(15);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(0xFF1F2937);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(nameLp);

        // Checkmark badge
        TextView tvCheck = new TextView(this);
        tvCheck.setText("✓");
        tvCheck.setTextSize(14);
        tvCheck.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCheck.setTextColor(0xFFFFFFFF);
        tvCheck.setPadding(dp(8), dp(4), dp(8), dp(4));
        tvCheck.setBackground(roundedBg(0xFF1976F3, dp(10)));
        tvCheck.setVisibility(View.GONE);

        // Chevron
        TextView tvChevron = new TextView(this);
        tvChevron.setText("  ▾");
        tvChevron.setTextSize(14);
        tvChevron.setTextColor(0xFF9CA3AF);

        row.addView(tvEmoji);
        row.addView(tvName);
        row.addView(tvCheck);
        row.addView(tvChevron);

        // ── Sub-panel (collapsed by default) ──────────────────────────
        LinearLayout subPanel = new LinearLayout(this);
        subPanel.setOrientation(LinearLayout.VERTICAL);
        subPanel.setVisibility(View.GONE);
        subPanel.setBackgroundColor(0xFFF9FAFB);
        subPanel.setPadding(dp(14), dp(8), dp(14), dp(12));

        if (cat.name.equals("Others")) {
            buildOthersPanel(subPanel);
        } else {
            buildSubPanel(subPanel, cat);
        }

        subPanels.put(cat.name, subPanel);

        wrapper.addView(row);
        wrapper.addView(subPanel);
        llCategoryList.addView(wrapper);

        // ── Click: toggle select + expand ─────────────────────────────
        row.setOnClickListener(v -> {
            boolean nowSelected = selectedCategories.contains(cat.name);

            if (!nowSelected) {
                // Select + expand
                selectedCategories.add(cat.name);
                row.setBackground(roundedBg(0xFFEFF6FF, dp(14)));
                tvCheck.setVisibility(View.VISIBLE);
                tvChevron.setText("  ▴");
                subPanel.setVisibility(
                        (cat.subGroups.isEmpty() && !cat.name.equals("Others"))
                                ? View.GONE : View.VISIBLE);
            } else {
                // Deselect + collapse
                selectedCategories.remove(cat.name);
                removeSubcategoriesOf(cat);
                row.setBackground(roundedBg(0xFFFFFFFF, dp(14)));
                tvCheck.setVisibility(View.GONE);
                tvChevron.setText("  ▾");
                subPanel.setVisibility(View.GONE);
            }
            refreshDoneButton();
        });
    }

    // ── Sub-panel for normal categories ───────────────────────────────────

    private void buildSubPanel(LinearLayout panel, HawkerCategory cat) {
        for (SubGroup group : cat.subGroups) {
            // Group label
            TextView tvGroup = new TextView(this);
            tvGroup.setText(group.groupName);
            tvGroup.setTextSize(12);
            tvGroup.setTypeface(null, android.graphics.Typeface.BOLD);
            tvGroup.setTextColor(0xFF6B7280);
            tvGroup.setPadding(0, dp(10), 0, dp(4));
            panel.addView(tvGroup);

            // Items as chip-checkboxes
            for (String item : group.items) {
                addCheckItem(panel, item);
            }
        }
    }

    // ── Sub-panel for "Others" ─────────────────────────────────────────────

    private EditText etOthersName;
    private LinearLayout llOthersSubs;
    private EditText etOthersSubInput;

    private void buildOthersPanel(LinearLayout panel) {
        // Custom category name
        TextView tvLabel = new TextView(this);
        tvLabel.setText("Category name *");
        tvLabel.setTextSize(12);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setTextColor(0xFF6B7280);
        tvLabel.setPadding(0, dp(6), 0, dp(4));
        panel.addView(tvLabel);

        etOthersName = new EditText(this);
        etOthersName.setHint("e.g. Flower pot seller");
        etOthersName.setBackground(editBg());
        etOthersName.setPadding(dp(12), dp(10), dp(12), dp(10));
        etOthersName.setTextSize(14);
        panel.addView(etOthersName);

        etOthersName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                othersCustomName = s.toString().trim();
            }
        });

        // Sub-items heading
        TextView tvSubLabel = new TextView(this);
        tvSubLabel.setText("Add items you sell (optional)");
        tvSubLabel.setTextSize(12);
        tvSubLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSubLabel.setTextColor(0xFF6B7280);
        tvSubLabel.setPadding(0, dp(12), 0, dp(4));
        panel.addView(tvSubLabel);

        // Input + Add button
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        etOthersSubInput = new EditText(this);
        etOthersSubInput.setHint("Type an item name");
        etOthersSubInput.setBackground(editBg());
        etOthersSubInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        etOthersSubInput.setTextSize(14);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        etOthersSubInput.setLayoutParams(etLp);

        TextView btnAdd = new TextView(this);
        btnAdd.setText("+ Add");
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

        // Container for added sub-items
        llOthersSubs = new LinearLayout(this);
        llOthersSubs.setOrientation(LinearLayout.VERTICAL);
        panel.addView(llOthersSubs);

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

    // ── Checkbox item ──────────────────────────────────────────────────────

    private void addCheckItem(LinearLayout parent, String item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(6));
        row.setTag(item);

        // Custom checkbox visual
        TextView tvBox = new TextView(this);
        tvBox.setTextSize(18);
        tvBox.setText("☐");
        tvBox.setTextColor(0xFF9CA3AF);
        tvBox.setPadding(0, 0, dp(10), 0);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(item);
        tvLabel.setTextSize(14);
        tvLabel.setTextColor(0xFF374151);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(lLp);

        row.addView(tvBox);
        row.addView(tvLabel);
        parent.addView(row);

        row.setOnClickListener(v -> {
            boolean checked = selectedSubcategories.contains(item);
            if (!checked) {
                selectedSubcategories.add(item);
                tvBox.setText("☑");
                tvBox.setTextColor(0xFF1976F3);
                row.setBackgroundColor(0x0A1976F3);
            } else {
                selectedSubcategories.remove(item);
                tvBox.setText("☐");
                tvBox.setTextColor(0xFF9CA3AF);
                row.setBackgroundColor(0x00000000);
            }
            refreshDoneButton();
        });
    }

    // ── Remove all subcategories belonging to a deselected category ─────────

    private void removeSubcategoriesOf(HawkerCategory cat) {
        if (cat.name.equals("Others")) {
            for (String s : othersCustomSubs) selectedSubcategories.remove(s);
            othersCustomName = "";
            if (etOthersName != null) etOthersName.setText("");
            return;
        }
        for (SubGroup g : cat.subGroups) {
            selectedSubcategories.removeAll(g.items);
        }
        // Un-check checkboxes visually
        View panel = subPanels.get(cat.name);
        if (panel instanceof LinearLayout) {
            uncheckAll((LinearLayout) panel);
        }
    }

    private void uncheckAll(LinearLayout parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                Object tag = row.getTag();
                if (tag instanceof String) {
                    // It's a checkbox row
                    for (int j = 0; j < row.getChildCount(); j++) {
                        View v = row.getChildAt(j);
                        if (v instanceof TextView) {
                            String t = ((TextView) v).getText().toString();
                            if (t.equals("☑")) {
                                ((TextView) v).setText("☐");
                                ((TextView) v).setTextColor(0xFF9CA3AF);
                                row.setBackgroundColor(0x00000000);
                            }
                        }
                    }
                } else {
                    uncheckAll(row); // recurse into group containers
                }
            }
        }
    }

    // ── Done button state ──────────────────────────────────────────────────

    private void refreshDoneButton() {
        boolean hasCategory = !selectedCategories.isEmpty();
        tvDoneBtn.setBackground(roundedBg(
                hasCategory ? 0xFF1976F3 : 0xFFB0BEC5, dp(14)));
        int count = selectedCategories.size();
        String label = count == 0 ? "Select at least one category"
                : "Done  (" + count + " categor" + (count == 1 ? "y" : "ies") + " selected) →";
        tvDoneBtn.setText(label);
    }

    // ── Finish & return result ─────────────────────────────────────────────

    private void onDone() {
        if (selectedCategories.isEmpty()) {
            Toast.makeText(this, "Please select at least one category", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedCategories.contains("Others") && othersCustomName.isEmpty()) {
            Toast.makeText(this, "Please enter a name for your 'Others' category", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent result = new Intent();
        result.putStringArrayListExtra(EXTRA_CATEGORIES,    new ArrayList<>(selectedCategories));
        result.putStringArrayListExtra(EXTRA_SUBCATEGORIES, new ArrayList<>(selectedSubcategories));
        result.putExtra(EXTRA_OTHERS_NAME, othersCustomName);
        setResult(RESULT_OK, result);
        finish();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Drawing helpers
    // ══════════════════════════════════════════════════════════════════════

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