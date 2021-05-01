package com.example.covidinformationapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.covidinformationapp.api.CovidService
import com.example.covidinformationapp.databinding.ActivityMainBinding
import com.example.covidinformationapp.model.CovidData
import com.example.covidinformationapp.model.CovidSparkAdapter
import com.example.covidinformationapp.model.Metric
import com.example.covidinformationapp.model.TimeScale
import com.google.gson.GsonBuilder
import com.robinhood.ticker.TickerUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private const val BASE_URL = "https://covidtracking.com/api/v1/"
private const val TAG = "MainActivity"
private const val ALL_STATES: String = "All (NationWide)"

class MainActivity : AppCompatActivity() {
    private lateinit var currentShownData: List<CovidData>
    private lateinit var adapter: CovidSparkAdapter
    private lateinit var binding: ActivityMainBinding
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationalDailyData: List<CovidData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.app_description)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        val covidService = retrofit.create(CovidService::class.java)


        //Fetch national data
        covidService.getNationalData().enqueue(object : Callback<List<CovidData>> {
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }

            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "OnResponse $response")
                val nationalData = response.body()
                if (nationalData == null) {
                    Log.w(TAG, "Did not get a valid response body")
                    return
                }
                setUpEventListeners()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG, "Update Graph with national data")
                updateDisplayWithData(nationalDailyData)


            }

        })

        //Fetch state data

        covidService.getStatesData().enqueue(object : Callback<List<CovidData>> {
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }

            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "OnResponse $response")
                val statesData = response.body()
                if (statesData == null) {
                    Log.w(TAG, "Did not get a valid response body")
                    return
                }
                perStateDailyData = statesData.reversed().groupBy { it.state }
                Log.i(TAG, "Update Spinner with data")
                updateSpinnerWithStateDate(perStateDailyData.keys)

            }

        })
    }

    private fun updateSpinnerWithStateDate(stateNames: Set<String>) {
        val stateAbbreviationList = stateNames.toMutableList()
        stateAbbreviationList.sort()
        stateAbbreviationList.add(0,ALL_STATES)
        //Add state list as data for our spinner
        binding.spinnerSelect.attachDataSource(stateAbbreviationList)
        binding.spinnerSelect.setOnSpinnerItemSelectedListener { parent, _, position, _ ->
            val selectedState = parent.getItemAtPosition(position) as String
            val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
            updateDisplayWithData(selectedData)
        }
    }

    private fun setUpEventListeners() {
        binding.tickerView.setCharacterLists(TickerUtils.provideNumberList())
        //Add listener for user scrubbing on the chart
        binding.sparkView.isScrubEnabled = true
        binding.sparkView.setScrubListener { itemData ->
            if (itemData is CovidData) {
                updateInfoForDate(itemData)
            }
        }
        //Respond to radio butons selected events
        binding.radioGroupTimeSelected.setOnCheckedChangeListener { _, checkedId ->
            adapter.daysAgo = when (checkedId) {
                binding.radioButtonWeek.id -> TimeScale.WEEK
                binding.radioButtonMonth.id -> TimeScale.MONTH
                else -> TimeScale.MAX
            }
            updateInfoForDate(currentShownData.last())
            adapter.notifyDataSetChanged()
        }

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.radioButtonNegative.id -> updateDisplayMetric(Metric.NEGATIVE)
                binding.radioButtonPostive.id -> updateDisplayMetric(Metric.POSITIVE)
                binding.radioButtonDeath.id -> updateDisplayMetric(Metric.DEATH)
            }
        }

    }

    private fun updateDisplayMetric(metric: Metric) {
        //Display The color of the chart
        val colorRes = when(metric){
            Metric.NEGATIVE -> R.color.green
            Metric.POSITIVE -> R.color.red
            Metric.DEATH -> R.color.death
        }
        @ColorInt val colorInt = ContextCompat.getColor(this,colorRes)
        binding.sparkView.lineColor = colorInt
        binding.tickerView.setTextColor(colorInt)
        adapter.metric = metric
        adapter.notifyDataSetChanged()
        updateInfoForDate(currentShownData.last())
    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentShownData = dailyData
        //Create a New Spark Adapter with the Data
        adapter = CovidSparkAdapter(dailyData)
        binding.sparkView.adapter = adapter
        // updated radio buttons to select the positives cases and max time by default
        binding.radioButtonPostive.isChecked = true
        binding.radioButtonMax.isChecked = true
        // Display the metric for the most recent date
        updateDisplayMetric(Metric.POSITIVE)
    }

    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when (adapter.metric) {
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.DEATH -> covidData.deathIncrease
        }
        binding.tickerView.text = NumberFormat.getInstance().format(numCases)
        val outPutDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        binding.tvDateLabel.text = outPutDateFormat.format(covidData.dateChecked)
    }
}