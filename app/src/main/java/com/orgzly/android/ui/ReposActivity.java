package com.orgzly.android.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.orgzly.BuildConfig;
import com.orgzly.R;
import com.orgzly.android.Shelf;
import com.orgzly.android.provider.clients.ReposClient;
import com.orgzly.android.repos.ContentRepo;
import com.orgzly.android.repos.DirectoryRepo;
import com.orgzly.android.repos.DropboxRepo;
import com.orgzly.android.repos.GitRepo;
import com.orgzly.android.repos.MockRepo;
import com.orgzly.android.repos.Repo;
import com.orgzly.android.repos.RepoFactory;
import com.orgzly.android.ui.dialogs.SimpleOneLinerDialog;
import com.orgzly.android.ui.fragments.DirectoryRepoFragment;
import com.orgzly.android.ui.fragments.GitRepoFragment;
import com.orgzly.android.ui.fragments.browser.FileBrowserFragment;
import com.orgzly.android.ui.fragments.ReposFragment;
import com.orgzly.android.ui.fragments.browser.FileBrowserFragment;
import com.orgzly.android.ui.util.ActivityUtils;
import com.orgzly.android.util.LogUtils;
import com.orgzly.android.util.UriUtils;

import java.io.File;

/**
 * Configuring repositories.
 */
public class ReposActivity extends RepoActivity
        implements
        SimpleOneLinerDialog.SimpleOneLinerDialogListener,
        DirectoryRepoFragment.DirectoryRepoFragmentListener,
        GitRepoFragment.GitRepoFragmentListener,
        FileBrowserFragment.BrowserFragmentListener,
        ReposFragment.ReposFragmentListener,
        FileBrowserOpener {

    public static final String TAG = ReposActivity.class.getName();

    public static final int ACTION_OPEN_DOCUMENT_TREE_REQUEST_CODE = 0;

    private static final int DIALOG_CREATE_DIRECTORY_ID = 1;
    private static final String DIALOG_CREATE_DIRECTORY_ARG_DIRECTORY = "directory";

    private Shelf mShelf;

    private int browserResultHandlerId;
    private BrowserResultHandler browserResultHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_repos);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        getSupportActionBar().setTitle(R.string.repositories);

        mShelf = new Shelf(getApplicationContext());

        if (savedInstanceState == null) {
            Fragment fragment = ReposFragment.getInstance();

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.activity_repos_frame, fragment, ReposFragment.FRAGMENT_TAG)
                    .commit();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                ActivityUtils.INSTANCE.closeSoftKeyboard(this);
                super.onBackPressed();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRepoCreateRequest(Repo repo) {
        addRepoUrl(repo.getUri().toString());

        popBackStackAndCloseKeyboard();
    }

    @Override
    public void onRepoUpdateRequest(long id, Repo repo) {
        updateRepoUrl(id, repo.getUri().toString());
        popBackStackAndCloseKeyboard();
    }

    @Override
    public void onRepoCancelRequest() {
        popBackStackAndCloseKeyboard();
    }

    @Override
    public void onRepoNewRequest(int id) {
        switch (id) {
            case R.id.repos_options_menu_item_new_dropbox:
                DropboxRepoActivity.start(this);
                return;
            case R.id.repos_options_menu_item_new_external_storage_directory:
                displayRepoFragment(DirectoryRepoFragment.getInstance(), DirectoryRepoFragment.FRAGMENT_TAG);
                return;
            case R.id.repos_options_menu_item_new_git:
                displayRepoFragment(GitRepoFragment.getInstance(), GitRepoFragment.FRAGMENT_TAG);
                return;
            default:
                throw new IllegalArgumentException("Unknown repo menu item clicked: " + id);
        }
    }

    @Override
    public void onRepoDeleteRequest(long id) {
        deleteRepo(id);
    }

    @SuppressLint("StaticFieldLeak")
    private void deleteRepo(final long id) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                mShelf.deleteRepo(id);
                return null;
            }
        }.execute();
    }

    @Override
    public void onRepoEditRequest(long id) {
        String url = ReposClient.getUrl(this, id);

        Repo repo = RepoFactory.getFromUri(this, url);

        if (repo instanceof DropboxRepo || repo instanceof MockRepo) {  // TODO: Remove Mock from here
            DropboxRepoActivity.start(this, id);
        } else if (repo instanceof DirectoryRepo || repo instanceof ContentRepo) {
            displayRepoFragment(DirectoryRepoFragment.getInstance(id), DirectoryRepoFragment.FRAGMENT_TAG);
        } else if (repo instanceof GitRepo) {
            displayRepoFragment(GitRepoFragment.getInstance(id), GitRepoFragment.FRAGMENT_TAG);
        } else {
            showSimpleSnackbarLong(R.string.message_unsupported_repository_type);
        }
    }

    private void displayRepoFragment(Fragment fragment, String tag) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit, R.anim.fragment_enter, R.anim.fragment_exit)
                .addToBackStack(null)
                .replace(R.id.activity_repos_frame, fragment, tag)
                .commit();
    }

    @Override
    public void onBrowserCancel() {
        getSupportFragmentManager().popBackStack();
    }

    @Override
    public void onBrowserCreate(String currentItem) {
        Bundle bundle = new Bundle();
        bundle.putString(DIALOG_CREATE_DIRECTORY_ARG_DIRECTORY, currentItem);

        SimpleOneLinerDialog
                .getInstance(DIALOG_CREATE_DIRECTORY_ID, R.string.new_folder, R.string.name, R.string.create, R.string.cancel, null, bundle)
                .show(getSupportFragmentManager(), SimpleOneLinerDialog.FRAGMENT_TAG);
    }

    @Override
    public void onBrowserUse(String item) {
        Uri uri = UriUtils.uriFromPath(DirectoryRepo.SCHEME, item);
        browserResultHandler.handleBrowseResult(uri);
        getSupportFragmentManager().popBackStack();
    }

    @Override
    public void onSimpleOneLinerDialogValue(int id, String value, Bundle bundle) {
        String currentDir = bundle.getString(DIALOG_CREATE_DIRECTORY_ARG_DIRECTORY);

        File file = new File(currentDir, value);

        if (file.mkdir()) {
            Fragment f = getSupportFragmentManager().findFragmentByTag(FileBrowserFragment.FRAGMENT_TAG);

            if (f != null) {
                FileBrowserFragment fragment = (FileBrowserFragment) f;
                fragment.refresh();
            }

        } else {
            String message = getResources().getString(
                    R.string.failed_creating_directory,
                    file.toString());

            showSimpleSnackbarLong(message);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, requestCode, resultCode, data);

        switch (requestCode) {
            case ACTION_OPEN_DOCUMENT_TREE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    Uri treeUri = data.getData();

                    // Persist permissions
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        grantUriPermission(getPackageName(), treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                              Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

                        getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                    }

                    DirectoryRepoFragment fragment =
                            (DirectoryRepoFragment) getSupportFragmentManager()
                                    .findFragmentByTag(DirectoryRepoFragment.FRAGMENT_TAG);

                    fragment.updateUri(treeUri);
                }

                break;
        }
    }

    @Override
    public void browseDirectory(Uri uri, BrowserResultHandler handler) {
        browserResultHandler = handler;
        String dir = uri != null ? uri.getPath() : null;
        getSupportFragmentManager()
                .beginTransaction()
                .addToBackStack(null)
                .replace(R.id.activity_repos_frame, FileBrowserFragment.getInstance(dir), FileBrowserFragment.FRAGMENT_TAG)
                .commit();
    }
}
