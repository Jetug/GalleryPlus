package com.jetug.gallery.pro.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.jetug.commons.dialogs.CreateNewFolderDialog
import com.jetug.commons.extensions.*
import com.jetug.commons.helpers.*
import com.jetug.commons.models.FileDirItem
import com.jetug.commons.views.MyGridLayoutManager
import com.jetug.commons.views.MyRecyclerView
import com.jetug.gallery.pro.R
import com.jetug.gallery.pro.activities.contracts.PickDirectoryContract
import com.jetug.gallery.pro.adapters.MediaAdapter
import com.jetug.gallery.pro.asynctasks.GetMediaAsynctask
import com.jetug.gallery.pro.asynctasks.GetMediaAsynctask2
import com.jetug.gallery.pro.databases.GalleryDatabase
import com.jetug.gallery.pro.dialogs.ChangeGroupingDialog
import com.jetug.gallery.pro.dialogs.ChangeSortingDialog
import com.jetug.gallery.pro.dialogs.ChangeViewTypeDialog
import com.jetug.gallery.pro.dialogs.FilterMediaDialog
import com.jetug.gallery.pro.extensions.*
import com.jetug.gallery.pro.helpers.*
import com.jetug.gallery.pro.interfaces.MediaOperationsListener
import com.jetug.gallery.pro.jetug.*
import com.jetug.gallery.pro.models.Medium
import com.jetug.gallery.pro.models.ThumbnailItem
import com.jetug.gallery.pro.models.ThumbnailSection
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_media.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.system.measureTimeMillis

class MediaActivity : SimpleActivity(), MediaOperationsListener {
    private val LAST_MEDIA_CHECK_PERIOD = 3000L
    private val IS_SWIPEREFRESH_ENABLED = false

    private var mPath = ""
    private var mIsGetAnyIntent = false
    private var mIsGettingMedia = false
    private var mAllowPickingMultiple = false
    private var mShowAll = false
    private var mLoadedInitialPhotos = false
    private var mIsSearchOpen = false
    private var mLastSearchedText = ""
    private var mDateFormat = ""
    private var mTimeFormat = ""
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mCurrAsyncTask: GetMediaAsynctask2? = null
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null
    private var mSearchMenuItem: MenuItem? = null

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredShowFileTypes = true
    private var mStoredRoundedCorners = false
    private var mStoredTextColor = 0
    private var mStoredAdjustedPrimaryColor = 0
    private var mStoredThumbnailSpacing = 0


    //////
    //lateinit var activityLauncher: ActivityResultLauncher<String>
    //////

    var mIsGetImageIntent = false
    var mIsGetVideoIntent = false

    companion object {
        var mMedia = ArrayList<ThumbnailItem>()
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        val elapsedTime = measureTimeMillis {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_media)

//            activityLauncher = registerForActivityResult(PickDirectoryContract()) { destination ->
//                if(destination != null)
//                    handleSAFDialog(source) {
//                        if (it) {
//                            copyMoveFilesTo(fileDirItems, source.trimEnd('/'), destination, isCopyOperation, true, config.shouldShowHidden, callback)
//                        }
//                    }
//            }

            intent.apply {
                mIsGetImageIntent = getBooleanExtra(GET_IMAGE_INTENT, false)
                mIsGetVideoIntent = getBooleanExtra(GET_VIDEO_INTENT, false)
                mIsGetAnyIntent = getBooleanExtra(GET_ANY_INTENT, false)
                mAllowPickingMultiple = getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            }

            media_refresh_layout.isEnabled = IS_SWIPEREFRESH_ENABLED
            media_refresh_layout.setOnRefreshListener { getMedia() }
            try {
                mPath = intent.getStringExtra(DIRECTORY) ?: ""
            } catch (e: Exception) {
                showErrorToast(e)
                finish()
                return
            }

            storeStateVariables()

            if (mShowAll) {
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                registerFileUpdateListener()
            }

            media_empty_text_placeholder_2.setOnClickListener {
                showFilterMediaDialog()
            }

            updateWidgets()
        }
        Log.e("Jet","Media on Create $elapsedTime ms")
    }

    override fun onStart() {
        super.onStart()
        //restoreRVPosition()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        val elapsedTime = measureTimeMillis {
            super.onResume()
            ///Jet
            makeTranslucentBars()
            restoreRVPosition()
            setTopPaddingToActionBarsHeight(media_grid)
            setTopMarginToActionBarsHeight(media_vertical_fastscroller)

            ///

            mDateFormat = config.dateFormat
            mTimeFormat = getTimeFormat()

            if (mStoredAnimateGifs != config.animateGifs) {
                mediaAdapter?.updateAnimateGifs(config.animateGifs)
            }

            if (mStoredCropThumbnails != config.cropThumbnails) {
                mediaAdapter?.updateCropThumbnails(config.cropThumbnails)
            }

            if (mStoredScrollHorizontally != config.scrollHorizontally) {
                mLoadedInitialPhotos = false
                media_grid.adapter = null
                getMedia()
            }

            if (mStoredShowFileTypes != config.showThumbnailFileTypes) {
                mediaAdapter?.updateShowFileTypes(config.showThumbnailFileTypes)
            }

            if (mStoredTextColor != config.textColor) {
                mediaAdapter?.updateTextColor(config.textColor)
            }

            val adjustedPrimaryColor = getAdjustedPrimaryColor()
            if (mStoredAdjustedPrimaryColor != adjustedPrimaryColor) {
                mediaAdapter?.updatePrimaryColor(config.primaryColor)
                media_horizontal_fastscroller.updatePrimaryColor(adjustedPrimaryColor)
                media_vertical_fastscroller.updatePrimaryColor(adjustedPrimaryColor)
            }

            if (mStoredThumbnailSpacing != config.thumbnailSpacing) {
                media_grid.adapter = null
                setupAdapter()
            }

            if (mStoredRoundedCorners != config.fileRoundedCorners) {
                media_grid.adapter = null
                setupAdapter()
            }

            media_horizontal_fastscroller.updateBubbleColors()
            media_vertical_fastscroller.updateBubbleColors()
            media_refresh_layout.isEnabled = config.enablePullToRefresh
            media_empty_text_placeholder.setTextColor(config.textColor)
            media_empty_text_placeholder_2.setTextColor(getAdjustedPrimaryColor())

            if (!mIsSearchOpen) {
                invalidateOptionsMenu()
            }

            if (mMedia.isEmpty() || this.getFolderSorting(mPath) and SORT_BY_RANDOM == 0) {
                if (shouldSkipAuthentication()) {
                    tryLoadGallery()
                } else {
                    handleLockedFolderOpening(mPath) { success ->
                        if (success) {
                            tryLoadGallery()
                        } else {
                            finish()
                        }
                    }
                }
            }
        }
        Log.e("Jet","Media on Resume $elapsedTime ms")
    }

    override fun onPause() {
        super.onPause()
        mIsGettingMedia = false
        media_refresh_layout.isRefreshing = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)

        if (!mMedia.isEmpty()) {
            mCurrAsyncTask?.stopFetching()
        }

        ///Jet
        saveRVPosition()
    }

    override fun onStop() {
        super.onStop()

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.tempSkipDeleteConfirmation = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (config.showAll && !isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.tempSkipDeleteConfirmation = false
            unregisterFileUpdateListener()
            GalleryDatabase.destroyInstance()
        }

        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        mMedia.clear()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_media, menu)

        val isDefaultFolder = !config.defaultFolder.isEmpty() && File(config.defaultFolder).compareTo(File(mPath)) == 0

        menu.apply {
            findItem(R.id.group).isVisible = !config.scrollHorizontally

            findItem(R.id.empty_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.empty_disable_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.restore_all_files).isVisible = mPath == RECYCLE_BIN

            findItem(R.id.folder_view).isVisible = mShowAll
            findItem(R.id.open_camera).isVisible = mShowAll
            findItem(R.id.about).isVisible = mShowAll
            findItem(R.id.create_new_folder).isVisible = !mShowAll && mPath != RECYCLE_BIN && mPath != FAVORITES

            findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden
            findItem(R.id.stop_showing_hidden).isVisible = config.temporarilyShowHidden

            findItem(R.id.set_as_default_folder).isVisible = !isDefaultFolder
            findItem(R.id.unset_as_default_folder).isVisible = isDefaultFolder

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            findItem(R.id.increase_column_count).isVisible = viewType == VIEW_TYPE_GRID && config.mediaColumnCnt < MAX_COLUMN_COUNT
            findItem(R.id.reduce_column_count).isVisible = viewType == VIEW_TYPE_GRID && config.mediaColumnCnt > 1
            findItem(R.id.toggle_filename).isVisible = viewType == VIEW_TYPE_GRID
        }

        setupSearch(menu)
        updateMenuItemColors(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.cab_change_order -> mediaAdapter?.changeOrder()
            R.id.sort -> showSortingDialog()
            R.id.filter -> showFilterMediaDialog()
            R.id.empty_recycle_bin -> emptyRecycleBin()
            R.id.empty_disable_recycle_bin -> emptyAndDisableRecycleBin()
            R.id.restore_all_files -> restoreAllFiles()
            R.id.toggle_filename -> toggleFilenameVisibility()
            R.id.open_camera -> launchCamera()
            R.id.folder_view -> switchToFolderView()
            R.id.change_view_type -> changeViewType()
            R.id.group -> showGroupByDialog()
            R.id.create_new_folder -> createNewFolder()
            R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
            R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
            R.id.increase_column_count -> increaseColumnCount()
            R.id.reduce_column_count -> reduceColumnCount()
            R.id.set_as_default_folder -> setAsDefaultFolder()
            R.id.unset_as_default_folder -> unsetAsDefaultFolder()
            R.id.slideshow -> startSlideshow()
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun startSlideshow() {
        if (mMedia.isNotEmpty()) {
            Intent(this, ViewPagerActivity::class.java).apply {
                val item = mMedia.firstOrNull { it is Medium } as? Medium ?: return
                putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
                putExtra(PATH, item.path)
                putExtra(SHOW_ALL, mShowAll)
                putExtra(SLIDESHOW_START_ON_ENTER, true)
                startActivity(this)
            }
        }
    }

    private fun storeStateVariables() {
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredShowFileTypes = showThumbnailFileTypes
            mStoredTextColor = textColor
            mStoredThumbnailSpacing = thumbnailSpacing
            mStoredRoundedCorners = fileRoundedCorners
            mShowAll = showAll
        }
        mStoredAdjustedPrimaryColor = getAdjustedPrimaryColor()
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        (mSearchMenuItem?.actionView as? SearchView)?.apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (mIsSearchOpen) {
                        mLastSearchedText = newText
                        searchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                mIsSearchOpen = true
                media_refresh_layout.isEnabled = false
                return true
            }

            // this triggers on device rotation too, avoid doing anything
            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                if (mIsSearchOpen) {
                    mIsSearchOpen = false
                    mLastSearchedText = ""

                    media_refresh_layout.isEnabled = config.enablePullToRefresh
                    searchQueryChanged("")
                }
                return true
            }
        })
    }

    private fun searchQueryChanged(text: String) {
        ensureBackgroundThread {
            try {
                val filtered = mMedia.filter { it is Medium && it.name.contains(text, true) } as ArrayList
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped = MediaFetcher(applicationContext).groupMedia(filtered as ArrayList<Medium>, mPath)
                runOnUiThread {
                    if (grouped.isEmpty()) {
                        media_empty_text_placeholder.text = getString(R.string.no_items_found)
                        media_empty_text_placeholder.beVisible()
                    } else {
                        media_empty_text_placeholder.beGone()
                    }

                    handleGridSpacing(grouped)
                    mediaAdapter?.updateMedia(grouped)
                    measureRecyclerViewContent(grouped)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun tryLoadGallery() {
        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                val dirName = when {
                    mPath == FAVORITES -> getString(R.string.favorites)
                    mPath == RECYCLE_BIN -> getString(R.string.recycle_bin)
                    mPath == config.OTGPath -> getString(R.string.usb)
                    else -> getHumanizedFilename(mPath)
                }
                updateActionBarTitle(if (mShowAll) resources.getString(R.string.all_folders) else dirName)
                getMedia()
                setupLayoutManager()
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    private val mediaAdapter get() = media_grid.adapter as? MediaAdapter

    private fun saveRVPosition(){
        val ox = media_grid.computeHorizontalScrollOffset()
        val oy = media_grid.computeVerticalScrollOffset()
        mediaScrollPositions[mPath] = Pair(ox, oy)
    }

    private fun restoreRVPosition(){
        val pos = mediaScrollPositions[mPath]
        if (pos != null) {
            (media_grid.layoutManager as MyGridLayoutManager).scrollToPositionWithOffset(pos.first, -pos.second)
        }
    }

    private fun setupAdapter() {
        if (!mShowAll && isDirEmpty()) {
            return
        }

        val currAdapter = media_grid.adapter
        if (currAdapter == null) {
            initZoomListener()
            val fastScroller = if (config.scrollHorizontally) media_horizontal_fastscroller else media_vertical_fastscroller
            MediaAdapter(this, mMedia.clone() as ArrayList<ThumbnailItem>, this, mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent,
                mAllowPickingMultiple, mPath, media_grid, fastScroller, media_refresh_layout) {
                if (it is Medium && !isFinishing) {
                    itemClicked(it.path)
                }
            }.apply {
                setupZoomListener(mZoomListener)
                media_grid.adapter = this
            }

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            if (viewType == VIEW_TYPE_LIST) {
                media_grid.scheduleLayoutAnimation()
            }

            setupLayoutManager()
            handleGridSpacing()
            measureRecyclerViewContent(mMedia)
        } else if (mLastSearchedText.isEmpty()) {
            (currAdapter as MediaAdapter).updateMedia(mMedia)
            handleGridSpacing()
            measureRecyclerViewContent(mMedia)
        } else {
            searchQueryChanged(mLastSearchedText)
        }
        setupScrollDirection()
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        val allowHorizontalScroll = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        media_vertical_fastscroller.isHorizontal = false
        media_vertical_fastscroller.beGoneIf(allowHorizontalScroll)

        media_horizontal_fastscroller.isHorizontal = true
        media_horizontal_fastscroller.beVisibleIf(allowHorizontalScroll)

        val sorting = this.getFolderSorting(if (mShowAll) SHOW_ALL else mPath)
        if (allowHorizontalScroll) {
            media_horizontal_fastscroller.setViews(media_grid, media_refresh_layout) {
                media_horizontal_fastscroller.updateBubbleText(getBubbleTextItem(it, sorting))
            }
        } else {
            media_vertical_fastscroller.setViews(media_grid, media_refresh_layout) {
                media_vertical_fastscroller.updateBubbleText(getBubbleTextItem(it, sorting))
            }
        }
    }

    private fun getBubbleTextItem(index: Int, sorting: Int): String {
        var realIndex = index
        val mediaAdapter = mediaAdapter
        if (mediaAdapter?.isASectionTitle(index) == true) {
            realIndex++
        }
        return mediaAdapter?.getItemBubbleText(realIndex, sorting, mDateFormat, mTimeFormat) ?: ""
    }

    private fun checkLastMediaChanged() {
        if (isDestroyed || this.getFolderSorting(mPath) and SORT_BY_RANDOM != 0) {
            return
        }

        mLastMediaHandler.removeCallbacksAndMessages(null)
        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getMedia()
                    }
                } else {
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showSortingDialog() {
        ChangeSortingDialog(this, false, true, mPath) {

            val adapter = mediaAdapter
            if(adapter != null) {
                getCachedMedia(mPath, mIsGetVideoIntent, mIsGetImageIntent) {
                    adapter.media = it
                    launchMain { adapter.notifyDataSetChanged() }
                }
            }

//            mLoadedInitialPhotos = false
//            media_grid.adapter = null
//            getMedia()
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mLoadedInitialPhotos = false
            media_refresh_layout.isRefreshing = true
            media_grid.adapter = null
            getMedia()
        }
    }

    private fun emptyRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyTheRecycleBin {
                finish()
            }
        }
    }

    private fun emptyAndDisableRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyAndDisableTheRecycleBin {
                finish()
            }
        }
    }

    private fun restoreAllFiles() {
        val paths = mMedia.filter { it is Medium }.map { (it as Medium).path } as ArrayList<String>
        restoreRecycleBinPaths(paths) {
            ensureBackgroundThread {
                directoryDao.deleteDirPath(RECYCLE_BIN)
            }
            finish()
        }
    }

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        mediaAdapter?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun switchToFolderView() {
        config.showAll = false
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, false, mPath) {
            invalidateOptionsMenu()
            setupLayoutManager()
            media_grid.adapter = null
            setupAdapter()
        }
    }

    private fun showGroupByDialog() {
        ChangeGroupingDialog(this, mPath) {
            mLoadedInitialPhotos = false
            media_grid.adapter = null
            getMedia()
        }
    }

    private fun deleteDirectoryIfEmpty() {
        if (config.deleteEmptyFolders) {
            val fileDirItem = FileDirItem(mPath, mPath.getFilenameFromPath(), true)
            if (!fileDirItem.isDownloadsFolder() && fileDirItem.isDirectory) {
                ensureBackgroundThread {
                    if (fileDirItem.getProperFileCount(this, true) == 0) {
                        tryDeleteFileDirItem(fileDirItem, true, true)
                    }
                }
            }
        }
    }

    fun getMedia() {
        if (mIsGettingMedia) {
            return
        }

        mIsGettingMedia = true
        if (mLoadedInitialPhotos) {
            startAsyncTask()
        } else {
            getCachedMedia(mPath, mIsGetVideoIntent, mIsGetImageIntent) {
                if (it.isEmpty()) {
                    runOnUiThread {
                        media_refresh_layout.isRefreshing = true
                    }
                } else {
                    gotMedia(it, true)
                }
                startAsyncTask()
            }
        }

        mLoadedInitialPhotos = true
    }

    fun isMediasEquals(newMedia: ArrayList<ThumbnailItem>): Boolean {
        val oldMedia = mMedia.clone() as ArrayList<ThumbnailItem>

        //if(newMedia.size == oldMedia.size) return false

        for(i in 0 until newMedia.size){
            val old = oldMedia[i]
            val new = newMedia[i]

            if(old is Medium && new is Medium && new.name != old.name)
                return false
        }

        return true
    }


    private fun startAsyncTask() {
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = GetMediaAsynctask2(applicationContext, mPath, mIsGetImageIntent, mIsGetVideoIntent, mShowAll, {
            //restoreRVPosition()
            ensureBackgroundThread {
                val oldMedia = mMedia.clone() as ArrayList<ThumbnailItem>
                val newMedia = it
                //if(isMediasEquals(newMedia)){
                    try {
                        gotMedia(newMedia, false)
                        oldMedia.filter { !newMedia.contains(it) }.mapNotNull { it as? Medium }.filter { !getDoesFilePathExist(it.path) }.forEach {
                            mediaDB.deleteMediumPath(it.path)
                        }
                    } catch (e: Exception) {
                    }
                //}
            }
        },
        {

            ///Jet

            ///
        })

        mCurrAsyncTask!!.execute()
    }

    private fun isDirEmpty(): Boolean {
        return if (mMedia.size <= 0 && config.filterMedia > 0) {
            if (mPath != FAVORITES && mPath != RECYCLE_BIN) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
            }

            if (mPath == FAVORITES) {
                ensureBackgroundThread {
                    directoryDao.deleteDirPath(FAVORITES)
                }
            }

            finish()
            true
        } else {
            false
        }
    }

    private fun deleteDBDirectory() {
        ensureBackgroundThread {
            try {
                directoryDao.deleteDirPath(mPath)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun createNewFolder() {
        CreateNewFolderDialog(this, mPath) {
            config.tempFolderPath = it
        }
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            handleHiddenFolderPasswordProtection {
                toggleTemporarilyShowHidden(true)
            }
        }
    }

    private fun toggleTemporarilyShowHidden(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowHidden = show
        getMedia()
        invalidateOptionsMenu()
    }

    private fun setupLayoutManager() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = media_grid.layoutManager as MyGridLayoutManager
        (media_grid.layoutParams as RelativeLayout.LayoutParams).apply {
            topMargin = 0
            bottomMargin = 0
        }

        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            media_refresh_layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            media_refresh_layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        layoutManager.spanCount = config.mediaColumnCnt
        val adapter = mediaAdapter
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter?.isASectionTitle(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun measureRecyclerViewContent(media: ArrayList<ThumbnailItem>) {
        media_grid.onGlobalLayout {
            if (config.scrollHorizontally) {
                calculateContentWidth(media)
            } else {
                calculateContentHeight(media)
            }
        }
    }

    private fun calculateContentWidth(media: ArrayList<ThumbnailItem>) {
        val layoutManager = media_grid.layoutManager as MyGridLayoutManager
        val thumbnailWidth = layoutManager.getChildAt(0)?.width ?: 0
        val spacing = config.thumbnailSpacing
        val fullWidth = ((media.size - 1) / layoutManager.spanCount + 1) * (thumbnailWidth + spacing) - spacing
        media_horizontal_fastscroller.setContentWidth(fullWidth)
        media_horizontal_fastscroller.setScrollToX(media_grid.computeHorizontalScrollOffset())
    }

    private fun calculateContentHeight(media: ArrayList<ThumbnailItem>) {
        val layoutManager = media_grid.layoutManager as MyGridLayoutManager
        val pathToCheck = if (mPath.isEmpty()) SHOW_ALL else mPath
        val hasSections = config.getFolderGrouping(pathToCheck) and GROUP_BY_NONE == 0 && !config.scrollHorizontally
        val sectionTitleHeight = if (hasSections) layoutManager.getChildAt(0)?.height ?: 0 else 0
        val thumbnailHeight = if (hasSections) layoutManager.getChildAt(1)?.height ?: 0 else layoutManager.getChildAt(0)?.height ?: 0

        var fullHeight = 0
        var curSectionItems = 0
        media.forEach {
            if (it is ThumbnailSection) {
                fullHeight += sectionTitleHeight
                if (curSectionItems != 0) {
                    val rows = ((curSectionItems - 1) / layoutManager.spanCount + 1)
                    fullHeight += rows * thumbnailHeight
                }
                curSectionItems = 0
            } else {
                curSectionItems++
            }
        }

        val spacing = config.thumbnailSpacing
        fullHeight += ((curSectionItems - 1) / layoutManager.spanCount + 1) * (thumbnailHeight + spacing) - spacing
        media_vertical_fastscroller.setContentHeight(fullHeight)
        media_vertical_fastscroller.setScrollToY(media_grid.computeVerticalScrollOffset())
    }

    private fun handleGridSpacing(media: ArrayList<ThumbnailItem> = mMedia) {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val spanCount = config.mediaColumnCnt
            val spacing = config.thumbnailSpacing
            val useGridPosition = media.firstOrNull() is ThumbnailSection

            var currentGridDecoration: GridSpacingItemDecoration? = null
            if (media_grid.itemDecorationCount > 0) {
                //currentGridDecoration = media_grid.getItemDecorationAt(0) as GridSpacingItemDecoration
                //currentGridDecoration.items = media
            }

            val newGridDecoration = GridSpacingItemDecoration(spanCount, spacing, config.scrollHorizontally, config.fileRoundedCorners, media, useGridPosition)
            if (currentGridDecoration.toString() != newGridDecoration.toString()) {
                if (currentGridDecoration != null) {
                    media_grid.removeItemDecoration(currentGridDecoration)
                }
                media_grid.addItemDecoration(newGridDecoration)
            }
        }
    }

    private fun initZoomListener() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val layoutManager = media_grid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        mediaAdapter?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        mediaAdapter?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = media_grid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        media_refresh_layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val smallMargin = resources.getDimension(R.dimen.small_margin).toInt()
        (media_grid.layoutParams as RelativeLayout.LayoutParams).apply {
            topMargin = smallMargin
            bottomMargin = smallMargin
        }

        mZoomListener = null
    }

    private fun increaseColumnCount() {
        config.mediaColumnCnt = ++(media_grid.layoutManager as MyGridLayoutManager).spanCount
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.mediaColumnCnt = --(media_grid.layoutManager as MyGridLayoutManager).spanCount
        columnCountChanged()
    }

    private fun columnCountChanged() {
        handleGridSpacing()
        invalidateOptionsMenu()
        mediaAdapter?.apply {
            notifyItemRangeChanged(0, media.size)
            measureRecyclerViewContent(media)
        }
    }

    private fun isSetWallpaperIntent() = intent.getBooleanExtra(SET_WALLPAPER_INTENT, false)

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE) {
            if (resultCode == Activity.RESULT_OK && resultData != null) {
                mMedia.clear()
                refreshItems()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun itemClicked(path: String) {
        val elapsedTime = measureTimeMillis {
            if (isSetWallpaperIntent()) {
                toast(R.string.setting_wallpaper)

                val wantedWidth = wallpaperDesiredMinimumWidth
                val wantedHeight = wallpaperDesiredMinimumHeight
                val ratio = wantedWidth.toFloat() / wantedHeight

                val options = RequestOptions()
                    .override((wantedWidth * ratio).toInt(), wantedHeight)
                    .fitCenter()

                Glide.with(this)
                    .asBitmap()
                    .load(File(path))
                    .apply(options)
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            try {
                                WallpaperManager.getInstance(applicationContext).setBitmap(resource)
                                setResult(Activity.RESULT_OK)
                            } catch (ignored: IOException) {
                            }

                            finish()
                        }
                    })
            } else if (mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent) {
                Intent().apply {
                    data = Uri.parse(path)
                    setResult(Activity.RESULT_OK, this)
                }
                finish()
            } else {
                val isVideo = path.isVideoFast()
                if (isVideo) {
                    val extras = HashMap<String, Boolean>()
                    extras[SHOW_FAVORITES] = mPath == FAVORITES

                    if (shouldSkipAuthentication()) {
                        extras[SKIP_AUTHENTICATION] = true
                    }
                    openPath(path, false, extras)
                } else {
                    Intent(this, ViewPagerActivity::class.java).apply {
                        putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
                        putExtra(PATH, path)
                        putExtra(SHOW_ALL, mShowAll)
                        putExtra(SHOW_FAVORITES, mPath == FAVORITES)
                        putExtra(SHOW_RECYCLE_BIN, mPath == RECYCLE_BIN)
                        startActivity(this)
                    }
                }
            }
        }
        Log.e("Jet","Media on Click $elapsedTime ms")
    }

    private fun gotMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean) {
        mIsGettingMedia = false
        checkLastMediaChanged()
        mMedia = media



        runOnUiThread {
            media_refresh_layout.isRefreshing = false
            media_empty_text_placeholder.beVisibleIf(media.isEmpty() && !isFromCache)
            media_empty_text_placeholder_2.beVisibleIf(media.isEmpty() && !isFromCache)

            if (media_empty_text_placeholder.isVisible()) {
                media_empty_text_placeholder.text = getString(R.string.no_media_with_filters)
            }
            media_grid.beVisibleIf(media_empty_text_placeholder.isGone())

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            val allowHorizontalScroll = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
            media_vertical_fastscroller.beVisibleIf(media_grid.isVisible() && !allowHorizontalScroll)
            media_horizontal_fastscroller.beVisibleIf(media_grid.isVisible() && allowHorizontalScroll)
            setupAdapter()
        }

        mLatestMediaId = getLatestMediaId()
        mLatestMediaDateId = getLatestMediaByDateId()
        if (!isFromCache) {
            val mediaToInsert = (mMedia).filter { it is Medium && it.deletedTS == 0L }.map { it as Medium }
            Thread {
                try {
                    mediaDB.insertAll(mediaToInsert)
                } catch (e: Exception) {
                }
            }.start()
        }

    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>) {
        val filtered = fileDirItems.filter { !getIsPathDirectory(it.path) && it.path.isMediaFile() } as ArrayList
        if (filtered.isEmpty()) {
            return
        }

        if (config.useRecycleBin && !filtered.first().path.startsWith(recycleBinPath)) {
            val movingItems = resources.getQuantityString(R.plurals.moving_items_into_bin, filtered.size, filtered.size)
            toast(movingItems)

            movePathsInRecycleBin(filtered.map { it.path } as ArrayList<String>) {
                if (it) {
                    deleteFilteredFiles(filtered)
                } else {
                    toast(R.string.unknown_error_occurred)
                }
            }
        } else {
            val deletingItems = resources.getQuantityString(R.plurals.deleting_items, filtered.size, filtered.size)
            toast(deletingItems)
            deleteFilteredFiles(filtered)
        }
    }

    private fun shouldSkipAuthentication() = intent.getBooleanExtra(SKIP_AUTHENTICATION, false)

    private fun deleteFilteredFiles(filtered: ArrayList<FileDirItem>) {
        deleteFiles(filtered) {
            if (!it) {
                toast(R.string.unknown_error_occurred)
                return@deleteFiles
            }

            mMedia.removeAll { filtered.map { it.path }.contains((it as? Medium)?.path) }

            ensureBackgroundThread {
                val useRecycleBin = config.useRecycleBin
                filtered.forEach {
                    if (it.path.startsWith(recycleBinPath) || !useRecycleBin) {
                        deleteDBPath(it.path)
                    }
                }
            }

            if (mMedia.isEmpty()) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
                finish()
            }
        }
    }

    override fun refreshItems() {
        getMedia()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        Intent().apply {
            putExtra(PICKED_PATHS, paths)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    override fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>) {
        var currentGridPosition = 0
        media.forEach {
            if (it is Medium) {
                it.gridPosition = currentGridPosition++
            } else if (it is ThumbnailSection) {
                currentGridPosition = 0
            }
        }

        if (media_grid.itemDecorationCount > 0) {
            val currentGridDecoration = media_grid.getItemDecorationAt(0) as GridSpacingItemDecoration
            currentGridDecoration.items = media
        }
    }

    private fun setAsDefaultFolder() {
        config.defaultFolder = mPath
        invalidateOptionsMenu()
    }

    private fun unsetAsDefaultFolder() {
        config.defaultFolder = ""
        invalidateOptionsMenu()
    }
}
