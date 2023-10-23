/**
 * Copyright 2016 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.sample;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.PDocSelection;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.listener.OnSearchMatchListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.github.barteksc.pdfviewer.scroll.ScrollHandle;
import com.shockwave.pdfium.PdfDocument;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.ViewById;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@EActivity(R.layout.activity_main)
public class PDFViewActivity extends AppCompatActivity implements OnLoadCompleteListener, OnPageErrorListener {

    private static final String TAG = PDFViewActivity.class.getSimpleName();

    private final static int REQUEST_CODE_PICK_PDF = 8081;
    private static final int PERMISSION_CODE_READ_EXTERNAL_STORAGE = 1000901;
    public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";

    public static final String SAMPLE_FILE_EN = "sample.pdf";
    public static final String SAMPLE_FILE_CH = "秋殇别恋 - Search.pdf";
    public static final String SAMPLE_FILE = SAMPLE_FILE_CH;

    @ViewById
    PDFView pdfView;
    @ViewById
    PDocSelection pdfSelection;

    @ViewById
    LinearLayout searchController;
    @ViewById
    View searchPrevButton;
    @ViewById
    View searchNextButton;

    Uri uri;

    String pdfFileName;

    int searchPage = -1;

    Integer currentPageNumber = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up title action bar & back icon
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.pdf_view_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.options, menu);
        MenuItem searchItem = menu.findItem(R.id.searchOnFile);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    pdfView.search(query);
                    searchController.setVisibility(View.VISIBLE);
                    // Toast.makeText(PDFViewActivity.this, query, Toast.LENGTH_SHORT).show();

                    setScrollHandleOffset(false);
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false;
                }
            });
            searchView.setOnCloseListener(new SearchView.OnCloseListener() {
                @Override
                public boolean onClose() {
                    pdfView.setIsSearching(false);
                    pdfView.closeTask();
                    searchPage = -1;
                    searchController.setVisibility(View.GONE);

                    setScrollHandleOffset(true);
                    return false;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        }
        if (itemId == R.id.pickPdfFile) {
            if (ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{READ_EXTERNAL_STORAGE}, PERMISSION_CODE_READ_EXTERNAL_STORAGE);
                return true;
            }
            launchFilePicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_PDF);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_pick_file_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void setScrollHandleOffset(boolean isReset) {
        ScrollHandle scrollHandle = pdfView.getScrollHandle();
        if (scrollHandle instanceof DefaultScrollHandle) {
            if (isReset) {
                ((DefaultScrollHandle) scrollHandle).offsetStopAt = 0;
                return;
            }
            if (_offsetStopAt == 0) {
                _offsetStopAt = (int) (_spacingBottom * getResources().getDisplayMetrics().density);
            }
            ((DefaultScrollHandle) scrollHandle).offsetStopAt = _offsetStopAt;
        }
    }

    @OnActivityResult(REQUEST_CODE_PICK_PDF)
    public void onResult(int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            uri = intent.getData();
            displayPdf(uri, SAMPLE_FILE);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            View customView = actionBar.getCustomView();
            if (customView != null) {
                TextView titleView = customView.findViewById(R.id.tvTitle);
                titleView.setText(title);
            }
            return;
        }
        super.setTitle(title);
    }

    @AfterViews
    void afterViews() {
        // display pdf
        pdfView.setSelectionPaintView(pdfSelection);
        pdfView.setBackgroundColor(Color.LTGRAY);
        displayPdf(uri, SAMPLE_FILE);

        // set title
        setTitle(pdfFileName);

        pdfView.setOnSelection(new PDFView.OnSelection() {
            @Override
            public void onSelection(boolean hasSelection) {
                if (hasSelection) {
                    String text = pdfView.getSelection();
                    Log.d(TAG, "Select text is: " + text);
                }

            }
        });

        searchPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goToNextSearchOccurrence(true);
            }
        });
        searchNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goToNextSearchOccurrence(false);
            }
        });
    }

    private void goToNextSearchOccurrence(boolean isPrevious) {
        if (!pdfView.isSearching) return;
        Set<Integer> keys = pdfView.searchRecords.keySet();
        Integer[] arr = new Integer[keys.size()];
        arr = keys.toArray(arr);
        List<Integer> myList = Arrays.asList(arr);
        Collections.sort(myList);
        int val = searchPage;
        if (myList.size() > 0) {
            int index = myList.indexOf(searchPage);

            if (isPrevious) {
                index = index - 1;
                if (index < 0) {
                    index = myList.size() - 1;
                }
            } else {
                index = index + 1;
                if (index >= myList.size()) {
                    index = 0;
                }
            }
            val = myList.get(index);
        } else {
            Toast.makeText(PDFViewActivity.this, "未搜索到匹配项", Toast.LENGTH_SHORT).show();
            return;
        }

        pdfView.jumpTo(val);
        searchPage = val;
    }

    static int _offsetStopAt = 0;
    static int _spacingBottom = 60;

    private void displayPdf(Uri uri, String assetFileName) {
        pdfFileName = uri != null ? getFileNameFromUri(uri) : assetFileName;
        PDFView.Configurator configurator = (uri != null ? pdfView.fromUri(uri) : pdfView.fromAsset(assetFileName));
        configurator.autoSpacing(false).spacing(10) // in dp
                .spacingTop(0).spacingBottom(_spacingBottom).enableAnnotationRendering(true).linkHandler(null)
                .scrollHandle(new DefaultScrollHandle(this)).onPageChange(new OnPageChangeListener() {
                    @Override
                    public void onPageChanged(int page, int pageCount) {
                        currentPageNumber = page;
                    }
                }).onLoad(this).onPageError(this).onSearchMatch(new OnSearchMatchListener() {
                    @Override
                    public void onSearchMatch(int page, String word) {
                        if (searchPage == -1) {
                            searchPage = page;
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    pdfView.jumpTo(page);
                                }
                            });
                        }
                    }
                }).load();
    }

    public String getFileNameFromUri(Uri uri) {
        if (uri == null) return null;
        String result = null;
        String scheme = uri.getScheme();
        if (scheme != null && scheme.equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    result = cursor.getString(index);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    /**
     * Listener for response to user permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchFilePicker();
            }
        }
    }

    /**
     * OnLoadCompleteListener
     */
    @Override
    public void loadComplete(int nbPages) {
        if (BuildConfig.DEBUG) {
            PdfDocument.Meta meta = pdfView.getDocumentMeta();
            Log.d(TAG, "title = " + meta.getTitle());
            Log.d(TAG, "author = " + meta.getAuthor());
            Log.d(TAG, "subject = " + meta.getSubject());
            Log.d(TAG, "keywords = " + meta.getKeywords());
            Log.d(TAG, "creator = " + meta.getCreator());
            Log.d(TAG, "producer = " + meta.getProducer());
            Log.d(TAG, "creationDate = " + meta.getCreationDate());
            Log.d(TAG, "modDate = " + meta.getModDate());
            printBookmarksTree(pdfView.getTableOfContents(), "-");
        }
    }

    public void printBookmarksTree(List<PdfDocument.Bookmark> tree, String sep) {
        for (PdfDocument.Bookmark b : tree) {
            Log.d(TAG, String.format("---> %s %s, p %d", sep, b.getTitle(), b.getPageIdx()));
            if (b.hasChildren()) {
                printBookmarksTree(b.getChildren(), sep + "-");
            }
        }
    }

    /**
     * OnPageErrorListener
     */

    @Override
    public void onPageError(int page, Throwable t) {
        Log.e(TAG, "Cannot load page " + page);
        /// TODO ... handle error
    }

}
