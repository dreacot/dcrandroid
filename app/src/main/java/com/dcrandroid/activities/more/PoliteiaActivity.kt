package com.dcrandroid.activities.more

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewTreeObserver
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.dcrandroid.R
import com.dcrandroid.activities.BaseActivity
import com.dcrandroid.adapter.ProposalAdapter
import com.dcrandroid.data.Proposal
import com.dcrandroid.extensions.hide
import com.dcrandroid.extensions.show
import com.dcrandroid.util.Deserializer
import com.dcrandroid.util.SnackBar
import com.dcrandroid.util.Utils
import com.google.gson.GsonBuilder
import dcrlibwallet.Dcrlibwallet
import dcrlibwallet.ProposalNotificationListener
import kotlinx.android.synthetic.main.activity_politeia.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList

class PoliteiaActivity : BaseActivity(), ProposalNotificationListener, SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemSelectedListener, ViewTreeObserver.OnScrollChangedListener {
    private lateinit var notificationManager: NotificationManager

    private var proposals = ArrayList<Proposal>()
    private var proposalAdapter: ProposalAdapter? = null
    private var layoutManager: LinearLayoutManager? = null

    private val gson = GsonBuilder().registerTypeHierarchyAdapter(ArrayList::class.java, Deserializer.ProposalDeserializer()).create()

    private var newestProposalsFirst = true
    private var loadedAll = false
    private var loadedAllInDiscussionProposals = false
    private var loadedAllActiveProposals = false
    private var loadedAllApprovedProposals = false
    private var loadedAllRejectedProposals = false
    private var loadedAllAbandonedProposals = false

    private val loading = AtomicBoolean(false)
    private val initialLoadingDone = AtomicBoolean(false)

    private val availableProposalTypes = ArrayList<String>()

    private var categorySortAdapter: ArrayAdapter<String>? = null

    private var progressStatus = 0
    private var currentCategory: Int = 0
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_politeia)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        swipe_refresh_layout.setOnRefreshListener(this)
        layoutManager = LinearLayoutManager(this)
        recycler_view.layoutManager = layoutManager
        proposalAdapter = ProposalAdapter(proposals, this)
        recycler_view.adapter = proposalAdapter
        recycler_view.viewTreeObserver.addOnScrollChangedListener(this)

        val timestampSortItems = resources.getStringArray(R.array.timestamp_sort)
        val timestampSortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timestampSortItems)
        timestampSortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        timestamp_sort_spinner.onItemSelectedListener = this
        timestamp_sort_spinner.adapter = timestampSortAdapter

        categorySortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableProposalTypes)
        categorySortAdapter!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        currentCategory = category_sort_spinner.selectedItemPosition
        category_sort_spinner.adapter = categorySortAdapter

        category_sort_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                currentCategory = position
                when (position) {
                    0 -> {
                        loadProposals(false, Dcrlibwallet.ProposalCategoryPre)
                    }
                    1 -> {
                        loadProposals(false, Dcrlibwallet.ProposalCategoryActive)
                    }
                    2 -> {
                        loadProposals(false, Dcrlibwallet.ProposalCategoryApproved)
                    }
                    3 -> {
                        loadProposals(false, Dcrlibwallet.ProposalCategoryRejected)
                    }
                    4 -> {
                        loadProposals(false, Dcrlibwallet.ProposalCategoryAbandoned)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        go_back.setOnClickListener {
            finish()
        }

        refreshAvailableProposalCategories()

        sync_layout.setOnClickListener {
            if (!multiWallet!!.isConnectedToDecredNetwork) {
                SnackBar.showError(this, R.string.not_connected)
                return@setOnClickListener
            }
            syncProposals()
        }
    }

    fun syncProposals() {
        Thread {
            try {
                runOnUiThread { SnackBar.showText(this, R.string.syncing_proposals) }
                multiWallet!!.politeia.sync()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                runOnUiThread { SnackBar.showError(this, R.string.error_syncying_proposals) }
            }
        }.start()
    }

    override fun onScrollChanged() {
        when (currentCategory) {
            0 -> {
                if (proposals.size < 5 || !initialLoadingDone.get()) return

                val firstVisibleItem = layoutManager!!.findFirstCompletelyVisibleItemPosition()
                if (firstVisibleItem != 0) {
                    proposals_page_header.elevation = resources.getDimension(R.dimen.app_bar_elevation)
                    app_bar.elevation = resources.getDimension(R.dimen.app_bar_elevation)
                } else {
                    proposals_page_header.elevation = 0f
                    app_bar.elevation = 0f
                }

                val lastVisibleItem = layoutManager!!.findLastCompletelyVisibleItemPosition()
                if (lastVisibleItem >= proposals.size - 1) {
                    if (!loadedAllInDiscussionProposals) {
                        recycler_view.stopScroll()
                        loadProposals(true, Dcrlibwallet.ProposalCategoryPre)
                    }
                }
            }
            1 -> {
                if (proposals.size < 5 || !initialLoadingDone.get()) return

                val firstVisibleItem = layoutManager!!.findFirstCompletelyVisibleItemPosition()
                if (firstVisibleItem != 0) {
                    proposals_page_header.elevation = resources.getDimension(R.dimen.app_bar_elevation)
                    app_bar.elevation = resources.getDimension(R.dimen.app_bar_elevation)
                } else {
                    proposals_page_header.elevation = 0f
                    app_bar.elevation = 0f
                }

                val lastVisibleItem = layoutManager!!.findLastCompletelyVisibleItemPosition()
                if (lastVisibleItem >= proposals.size - 1) {
                    if (!loadedAllActiveProposals) {
                        recycler_view.stopScroll()
                        loadProposals(true, Dcrlibwallet.ProposalCategoryActive)
                    }
                }
            }
            2 -> {
                if (proposals.size < 5 || !initialLoadingDone.get()) return

                val firstVisibleItem = layoutManager!!.findFirstCompletelyVisibleItemPosition()
                if (firstVisibleItem != 0) {
                    proposals_page_header.elevation = resources.getDimension(R.dimen.app_bar_elevation)
                    app_bar.elevation = resources.getDimension(R.dimen.app_bar_elevation)
                } else {
                    proposals_page_header.elevation = 0f
                    app_bar.elevation = 0f
                }

                val lastVisibleItem = layoutManager!!.findLastCompletelyVisibleItemPosition()
                if (lastVisibleItem >= proposals.size - 1) {
                    if (!loadedAllApprovedProposals) {
                        recycler_view.stopScroll()
                        loadProposals(true, Dcrlibwallet.ProposalCategoryApproved)
                    }
                }
            }
            3 -> {
                if (proposals.size < 5 || !initialLoadingDone.get()) return

                val firstVisibleItem = layoutManager!!.findFirstCompletelyVisibleItemPosition()
                if (firstVisibleItem != 0) {
                    proposals_page_header.elevation = resources.getDimension(R.dimen.app_bar_elevation)
                    app_bar.elevation = resources.getDimension(R.dimen.app_bar_elevation)
                } else {
                    proposals_page_header.elevation = 0f
                    app_bar.elevation = 0f
                }

                val lastVisibleItem = layoutManager!!.findLastCompletelyVisibleItemPosition()
                if (lastVisibleItem >= proposals.size - 1) {
                    if (!loadedAllRejectedProposals) {
                        recycler_view.stopScroll()
                        loadProposals(true, Dcrlibwallet.ProposalCategoryRejected)
                    }
                }
            }
            4 -> {
                if (proposals.size < 5 || !initialLoadingDone.get()) return

                val firstVisibleItem = layoutManager!!.findFirstCompletelyVisibleItemPosition()
                if (firstVisibleItem != 0) {
                    proposals_page_header.elevation = resources.getDimension(R.dimen.app_bar_elevation)
                    app_bar.elevation = resources.getDimension(R.dimen.app_bar_elevation)
                } else {
                    proposals_page_header.elevation = 0f
                    app_bar.elevation = 0f
                }

                val lastVisibleItem = layoutManager!!.findLastCompletelyVisibleItemPosition()
                if (lastVisibleItem >= proposals.size - 1) {
                    if (!loadedAllAbandonedProposals) {
                        recycler_view.stopScroll()
                        loadProposals(true, Dcrlibwallet.ProposalCategoryAbandoned)
                    }
                }
            }
        }
    }

    private fun loadProposals(loadMore: Boolean = false, proposalCategory: Int) = GlobalScope.launch(Dispatchers.Default) {
        runOnUiThread {
            showLoadingView()
            swipe_refresh_layout.isRefreshing = true
            swipe_refresh_layout.visibility = View.GONE
            empty_list.visibility = View.GONE
        }

        if (loading.get()) {
            return@launch
        }

        loading.set(true)
        val limit = 10
        val offset = when {
            loadMore -> proposals.size
            else -> 0
        }

        val jsonResult = multiWallet!!.politeia.getProposals(proposalCategory, offset, limit, newestProposalsFirst)

        // Check if the result object from the json response is null
        val resultObject = JSONObject(jsonResult).get("result")
        val resultObjectString = resultObject.toString()
        val tempProposalList = if (resultObjectString == "null") {
            gson.fromJson("[]", Array<Proposal>::class.java)
        } else {
            val resultArray = JSONObject(jsonResult).getJSONArray("result")
            val resultArrayString = resultArray.toString()
            gson.fromJson(resultArrayString, Array<Proposal>::class.java)
        }

        initialLoadingDone.set(true)

        if (tempProposalList == null) {
            loadedAll = true
            loading.set(false)
            hideLoadingView()

            if (!loadMore) {
                proposals.clear()
            }
            return@launch
        }

        if (tempProposalList.size < limit) {
            loadedAll = true
        }

        if (loadMore) {
            val positionStart = proposals.size
            proposals.addAll(tempProposalList)
            withContext(Dispatchers.Main) {
                proposalAdapter?.notifyItemRangeInserted(positionStart, tempProposalList.size)

                // notify previous last item to remove bottom margin
                proposalAdapter?.notifyItemChanged(positionStart - 1)
            }

        } else {
            proposals.let {
                it.clear()
                it.addAll(tempProposalList)
            }

            checkEmptyProposalList("")

            withContext(Dispatchers.Main) {
                proposalAdapter?.notifyDataSetChanged()
            }
        }

        loading.set(false)
        hideLoadingView()

        swipe_refresh_layout.isRefreshing = false
    }

    private fun checkEmptyProposalList(status: String) {
        runOnUiThread {
            if (proposals.size > 0) {
                swipe_refresh_layout?.show()
            } else {
                swipe_refresh_layout?.hide()
                empty_list.text = String.format(Locale.getDefault(), "No %s proposals", status)
            }
        }
    }

    private fun refreshAvailableProposalCategories() = GlobalScope.launch(Dispatchers.Default) {
        availableProposalTypes.clear()

        val preCount = multiWallet!!.politeia.count(Dcrlibwallet.ProposalCategoryPre)
        val activeCount = multiWallet!!.politeia.count(Dcrlibwallet.ProposalCategoryActive)
        val approvedCount = multiWallet!!.politeia.count(Dcrlibwallet.ProposalCategoryApproved)
        val rejectedCount = multiWallet!!.politeia.count(Dcrlibwallet.ProposalCategoryRejected)
        val abandonedCount = multiWallet!!.politeia.count(Dcrlibwallet.ProposalCategoryAbandoned)

        withContext(Dispatchers.Main) {
            availableProposalTypes.add(getString(R.string.proposal_in_discussion, preCount))
            availableProposalTypes.add(getString(R.string.proposal_active, activeCount))
            availableProposalTypes.add(getString(R.string.proposal_approved, approvedCount))
            availableProposalTypes.add(getString(R.string.proposal_rejected, rejectedCount))
            availableProposalTypes.add(getString(R.string.proposal_abandoned, abandonedCount))

            categorySortAdapter?.notifyDataSetChanged()
        }
    }

    private fun showLoadingView() {
        Thread(Runnable {
            progressStatus = 0

            while (progressStatus < 100) {
                runOnUiThread { loading_view.visibility = View.VISIBLE }

                progressStatus += 10
                // Update the progress bar and display the
                //current value in the text view
                handler.post {
                    progressBar.progress = progressStatus
                    textView.text = "Loading proposals"
                }
                try {
                    // Sleep for 200 milliseconds.
                    Thread.sleep(200)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }).start()
    }

    private fun hideLoadingView() {
        runOnUiThread {
            progressStatus = 100
            progressBar.progress = progressStatus
            textView.text = "" + progressStatus + "/" + progressBar.max
            loading_view.visibility = View.GONE
//            swipe_refresh_layout.visibility = View.VISIBLE
            if (proposals.size > 0) {
                swipe_refresh_layout.visibility = View.VISIBLE
                empty_list.visibility = View.GONE
            } else {
                swipe_refresh_layout.visibility = View.GONE
                empty_list.visibility = View.VISIBLE
            }
        }
    }

    override fun onRefresh() {
        when (currentCategory) {
            0 -> {
                loadProposals(false, Dcrlibwallet.ProposalCategoryPre)
//                loadInDiscussionProposals()
            }
            1 -> {
                loadProposals(false, Dcrlibwallet.ProposalCategoryActive)
//                loadActiveProposals()
            }
            2 -> {
                loadProposals(false, Dcrlibwallet.ProposalCategoryApproved)
//                loadApprovedProposals()
            }
            3 -> {
                loadProposals(false, Dcrlibwallet.ProposalCategoryRejected)
//                loadRejectedProposals()
            }
            4 -> {
                loadProposals(false, Dcrlibwallet.ProposalCategoryAbandoned)
//                loadAbandonedProposals()
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        when (currentCategory) {
            0 -> {
                if (!initialLoadingDone.get()) {
                    return
                }

                if (parent!!.id == R.id.timestamp_sort_spinner) {
                    val newestFirst = position == 0 // "Newest" is the first item
                    if (newestFirst != newestProposalsFirst) {
                        newestProposalsFirst = newestFirst
                        loadProposals(false, Dcrlibwallet.ProposalCategoryPre)
                    }
                }
            }
            1 -> {
                if (!initialLoadingDone.get()) {
                    return
                }

                if (parent!!.id == R.id.timestamp_sort_spinner) {
                    val newestFirst = position == 0 // "Newest" is the first item
                    if (newestFirst != newestProposalsFirst) {
                        newestProposalsFirst = newestFirst
                        loadProposals(false, Dcrlibwallet.ProposalCategoryActive)
                    }
                }
            }
            2 -> {
                if (!initialLoadingDone.get()) {
                    return
                }

                if (parent!!.id == R.id.timestamp_sort_spinner) {
                    val newestFirst = position == 0 // "Newest" is the first item
                    if (newestFirst != newestProposalsFirst) {
                        newestProposalsFirst = newestFirst
                        loadProposals(false, Dcrlibwallet.ProposalCategoryApproved)
                    }
                }
            }
            3 -> {
                if (!initialLoadingDone.get()) {
                    return
                }

                if (parent!!.id == R.id.timestamp_sort_spinner) {
                    val newestFirst = position == 0 // "Newest" is the first item
                    if (newestFirst != newestProposalsFirst) {
                        newestProposalsFirst = newestFirst
                        loadProposals(false, Dcrlibwallet.ProposalCategoryRejected)
                    }
                }
            }
            4 -> {
                if (!initialLoadingDone.get()) {
                    return
                }

                if (parent!!.id == R.id.timestamp_sort_spinner) {
                    val newestFirst = position == 0 // "Newest" is the first item
                    if (newestFirst != newestProposalsFirst) {
                        newestProposalsFirst = newestFirst
                        loadProposals(false, Dcrlibwallet.ProposalCategoryAbandoned)
                    }
                }
            }
        }
    }

    override fun onNewProposal(proposalID: Long, token: String?) {
        Utils.sendProposalNotification(this, notificationManager, proposalID, getString(R.string.new_proposal), token!!)
    }

    override fun onProposalVoteStarted(proposalID: Long, token: String?) {
        Utils.sendProposalNotification(this, notificationManager, proposalID, getString(R.string.vote_started), token!!)
    }

    override fun onProposalVoteFinished(proposalID: Long, token: String?) {
        Utils.sendProposalNotification(this, notificationManager, proposalID, getString(R.string.vote_ended), token!!)
    }
}