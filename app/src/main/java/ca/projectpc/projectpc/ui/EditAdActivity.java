package ca.projectpc.projectpc.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.pchmn.materialchips.ChipsInput;
import com.pchmn.materialchips.model.ChipInterface;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ca.projectpc.projectpc.R;
import ca.projectpc.projectpc.api.IServiceCallback;
import ca.projectpc.projectpc.api.Service;
import ca.projectpc.projectpc.api.ServiceResult;
import ca.projectpc.projectpc.api.ServiceTask;
import ca.projectpc.projectpc.api.service.PostService;
import ca.projectpc.projectpc.ui.glide.GlideApp;

public class EditAdActivity extends AppCompatActivity {
    private static final int MAX_IMAGES = 8;

    private LinearLayout mImageContainer;
    private EditText mTitleEditText;
    private EditText mDescriptionEditText;
    private Spinner mCurrenciesSpinner;
    private EditText mPriceEditText;
    private EditText mLocationEditText;
    private ChipsInput mTagsChipsInput;

    private ProgressDialog mProgressDialog;
    private ServiceTask mTask;

    private String mPostId;
    private List<ImageView> mImageViews;
    private Bitmap[] mImages;
    private File[] mChangedImages;
    private File mChangedThumbnailImage;
    private List<String> mTags;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_ad);

        // Enable back button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Get controls
        mTitleEditText = (EditText) findViewById(R.id.edit_post_title);
        mDescriptionEditText = (EditText) findViewById(R.id.edit_post_description);
        mCurrenciesSpinner = (Spinner) findViewById(R.id.edit_post_currencies);
        mPriceEditText = (EditText) findViewById(R.id.edit_post_price);
        mLocationEditText = (EditText) findViewById(R.id.edit_post_location);
        mTagsChipsInput = (ChipsInput) findViewById(R.id.edit_post_tags);

        // Do show detailed chip info
        mTagsChipsInput.setShowChipDetailed(false);

        // Check for valid tags
        mTagsChipsInput.addChipsListener(new ChipsInput.ChipsListener() {
            @Override
            public void onChipAdded(final ChipInterface chipInterface, int i) {
                mTags.add(chipInterface.getLabel());
            }

            @Override
            public void onChipRemoved(ChipInterface chipInterface, int i) {
                String label = chipInterface.getLabel();
                if (mTags.contains(label)) {
                    mTags.remove(label);
                }
            }

            @Override
            public void onTextChanged(CharSequence charSequence) {
                int length = charSequence.length();
                if (length > 0) {
                    char lastChar = charSequence.charAt(length - 1);
                    if (lastChar == ' ') {
                        String label = charSequence.subSequence(0, length - 1).toString();
                        mTagsChipsInput.addChip(label, "");
                    }
                }
            }
        });

        // Get image container
        mImageContainer = (LinearLayout) findViewById(R.id.edit_post_image_container);

        // Get image view size
        int imageViewWidth = (int) getResources().getDimension(R.dimen.post_image_width);
        int imageViewHeight = (int) getResources().getDimension(R.dimen.post_image_height);

        // Create image views
        mImageViews = new ArrayList<>();
        mImages = new Bitmap[MAX_IMAGES];
        for (int i = 0; i < MAX_IMAGES; i++) {
            ImageView imageView = new ImageView(this);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setImageResource(R.drawable.ic_add_box_gray);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(
                    imageViewWidth,
                    imageViewHeight
            ));
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // TODO: If image exists, show delete/set as main, otherwise show add image
                }
            });

            mImageContainer.addView(imageView);
            mImageViews.add(imageView);
        }

        Intent intent = getIntent();
        mPostId = intent.getStringExtra("postId");

        downloadAd(mPostId);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void pressBack() {
        super.onBackPressed();
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_alert_discard);
        builder.setMessage(R.string.prompt_discard_post_changes);
        builder.setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (i == AlertDialog.BUTTON_POSITIVE) {
                    pressBack();
                }
            }
        });
        builder.setNegativeButton(R.string.action_no, null);
        builder.show();
    }

    private void downloadAd(String postId) {
        // Show progress dialog
        mProgressDialog = ProgressDialog.show(this,
                getString(R.string.title_progress_posting),
                getString(R.string.prompt_wait), true, true,
                new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if (mTask != null && !mTask.isCancelled()) {
                            mTask.cancel();
                        }
                    }
                });

        // Download ad information
        try {
            final Context context = this;
            PostService service = Service.get(PostService.class);
            mTask = service.getPost(mPostId,
                    new IServiceCallback<PostService.GetPostResult>() {
                        @Override
                        public void onEnd(ServiceResult<PostService.GetPostResult> result) {
                            if (mProgressDialog != null) {
                                mProgressDialog.dismiss();
                            }

                            if (result.isCancelled()) {
                                return;
                            }

                            if (!result.hasError()) {
                                // Get data
                                PostService.GetPostResult data = result.getData();

                                // Get currency
                                SpinnerAdapter currencyAdapter = mCurrenciesSpinner.getAdapter();
                                int currencyIndex = 0;
                                for (; currencyIndex < currencyAdapter.getCount();
                                     currencyIndex++) {
                                    String currency = (String) currencyAdapter.getItem(currencyIndex);
                                    if (currency.equals(data.currency)) {
                                        break;
                                    }
                                }

                                // Update UI
                                mTitleEditText.setText(data.title);
                                mDescriptionEditText.setText(data.body);
                                mCurrenciesSpinner.setSelection(currencyIndex);
                                mPriceEditText.setText(data.price.toString());
                                if (data.location != null) {
                                    mLocationEditText.setText(data.location);
                                }

                                // Add tags
                                for (String tag : data.tags) {
                                    mTagsChipsInput.addChip(tag, "");
                                }

                                // Download images
                                for (int i = 0; i < data.imageIds.length; i++) {
                                    String imageId = data.imageIds[i];
                                    downloadImage(imageId, i, i == data.imageIds.length - 1);
                                }
                            } else {
                                // The API failed to complete the request and returned an
                                // exception
                                result.getException().printStackTrace();
                                Toast.makeText(
                                        context,
                                        R.string.service_unable_to_process_request,
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                    });
        } catch (Exception ex) {
            // Unable to get service (internal error)
            ex.printStackTrace();
            Toast.makeText(this, R.string.service_internal_error, Toast.LENGTH_LONG).show();
        }
    }

    private void downloadImage(String imageId, final int index, final boolean lastImage) {
        try {
            final Context context = this;
            final PostService service = Service.get(PostService.class);
            mTask = service.downloadImage(imageId, new IServiceCallback<PostService.DownloadImageResult>() {
                @Override
                public void onEnd(ServiceResult<PostService.DownloadImageResult> result) {
                    mTask = null;
                    if (result.isCancelled()) {
                        return;
                    }
                    if (!result.hasError()) {
                        // Decode image
                        byte[] buffer = Base64.decode(result.getData().imageData, Base64.DEFAULT);

                        // Show in image view
                        ImageView imageView = mImageViews.get(index);
                        GlideApp.with(context)
                                .load(buffer)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .into(imageView);

                        // Dismiss progress if this was the last image downloaded
                        if (lastImage && mProgressDialog != null) {
                            mProgressDialog.dismiss();
                            mProgressDialog = null;
                        }
                    } else {
                        result.getException().printStackTrace();
                        Toast.makeText(context,
                                getString(R.string.service_unable_to_process_request),
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        } catch (Exception ex) {
            // Unable to get service (internal error)
            ex.printStackTrace();
            Toast.makeText(this, R.string.service_internal_error,
                    Toast.LENGTH_LONG).show();
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
        }
    }

    public void onSave(View view) {
        // TODO: Show are you sure message (are you sure you wish to post ad/make the changes?)


        // TODO/NOTE: When saving an ad, unlist it first

        // TODO: Upload...
        Toast.makeText(this, "Saving ad...", Toast.LENGTH_LONG).show();
        finish();
    }
}
