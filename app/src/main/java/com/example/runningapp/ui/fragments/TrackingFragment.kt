package com.example.runningapp.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.example.runningapp.R
import com.example.runningapp.databinding.FragmentTrackingBinding
import com.example.runningapp.db.Run
import com.example.runningapp.other.Constants.ACTION_PAUSE_SERVICE
import com.example.runningapp.other.Constants.ACTION_START_OR_RESUME_SERVICE
import com.example.runningapp.other.Constants.ACTION_STOP_SERVICE
import com.example.runningapp.other.Constants.MAP_ZOOM
import com.example.runningapp.other.Constants.POLYLINE_COLOR
import com.example.runningapp.other.Constants.POLYLINE_WIDTH
import com.example.runningapp.other.TrackingUtility
import com.example.runningapp.services.Polyline
import com.example.runningapp.services.TrackingService
import com.example.runningapp.ui.viewModels.MainViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.lang.Math.round
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class TrackingFragment:Fragment(R.layout.fragment_tracking) {

    private lateinit var binding:FragmentTrackingBinding
    private val viewModel: MainViewModel by viewModels()

    private var isTracking = false
    private var pathPoints = mutableListOf<Polyline>()

    private var map:GoogleMap? = null
    private var curTimeInMillis = 0L

    private var menu: Menu? = null


    @set:Inject
    var weight = 80f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTrackingBinding.inflate(inflater,container,false)
        val view = binding.root

        setHasOptionsMenu(true)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapView.onCreate(savedInstanceState)
        binding.btnToggleRun.setOnClickListener {
            toggleRun()
        }

        binding.btnFinishRun.setOnClickListener {
            zoomToSeeWholeTrack()
            endRunAndSaveToDb()
        }
        binding.mapView.getMapAsync {
            map = it
            addAllPolyLines()
        }

        subcribeToObserves()
    }

    private fun subcribeToObserves(){
        TrackingService.isTracking.observe(viewLifecycleOwner, Observer {
            updateTracking(it)
        })

        TrackingService.pathPoints.observe(viewLifecycleOwner, Observer {
            pathPoints = it
            addLatestPolyLine()
            moveCameraToUser()
        })

        TrackingService.timeRunInMillis.observe(viewLifecycleOwner, Observer {
            curTimeInMillis = it
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(curTimeInMillis, true)
            binding.tvTimer.text = formattedTime
        })
    }

    private fun toggleRun(){
        if (isTracking){
            menu?.getItem(0)?.isVisible = true
            sendCommandToService(ACTION_PAUSE_SERVICE)
        } else{
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.toolbar_tracking_menu, menu)
        this.menu = menu
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if (curTimeInMillis > 0L){
            this.menu?.getItem(0)?.isVisible = true

        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.miCancelTracking -> {
                showCancelTrackingDialogue()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showCancelTrackingDialogue(){
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setTitle("Cancel the Run?")
            .setMessage("Are you sure to cancel the run and delete all its data?")
            .setIcon(R.drawable.ic_delete)
            .setPositiveButton("Yes"){_,_ ->
                stopRun()
            }
            .setNegativeButton("No"){ dialogInterface,_ ->
                dialogInterface.cancel()
            }
            .create()
        dialog.show()
    }

    private fun stopRun(){
        sendCommandToService(ACTION_STOP_SERVICE)
        findNavController().navigate(R.id.action_trackingFragment_to_runFragment)

    }

    private fun updateTracking(isTracking: Boolean){
        this.isTracking = isTracking
        if (!isTracking){
            binding.btnToggleRun.text = "Start"
            binding.btnFinishRun.visibility = View.VISIBLE
        } else {
            binding.btnToggleRun.text = "Stop"
            menu?.getItem(0)?.isVisible = true
            binding.btnFinishRun.visibility = View.GONE
        }
    }

    private fun moveCameraToUser(){
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()){
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    MAP_ZOOM
                )
            )
        }
    }

    private fun zoomToSeeWholeTrack(){
        val bounds = LatLngBounds.Builder()
        for (polyline in pathPoints){
            for (pos in polyline){
                bounds.include(pos)
            }
        }

        map?.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                binding.mapView.width,
                binding.mapView.height,
                (binding.mapView.height * 0.05f).toInt()
            )
        )
    }

    private fun endRunAndSaveToDb(){
        map?.snapshot { bmp ->
            var distanceInMeters = 0
            for (polyline in pathPoints){
                distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()
            }

            val avgSpeed = round((distanceInMeters / 1000f) / (curTimeInMillis/1000f/60/60) * 10) / 10f
            val dateTimeStamp = Calendar.getInstance().timeInMillis
            val caloriesBurned = ((distanceInMeters / 1000f) * weight).toInt()
            val run = Run(bmp, dateTimeStamp,avgSpeed,distanceInMeters,curTimeInMillis,caloriesBurned)
            viewModel.insertRun(run)
            Snackbar.make(
                requireActivity().findViewById(R.id.rootView),
                "Run saved successfully",
                Snackbar.LENGTH_LONG
            ).show()
            stopRun()
        }
    }

    private fun addAllPolyLines(){
        for (polyLine in pathPoints){
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyLine)
            map?.addPolyline(polylineOptions)
        }
    }

   private fun addLatestPolyLine(){
       if (pathPoints.isNotEmpty() && pathPoints.last().size >1){
           val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
           val lastLatLng = pathPoints.last().last()
           val polylineOptions = PolylineOptions()
               .color(POLYLINE_COLOR)
               .width(POLYLINE_WIDTH)
               .add(preLastLatLng)
               .add(lastLatLng)
           map?.addPolyline(polylineOptions)

       }
   }

    private fun sendCommandToService(action: String) =
        Intent(requireContext(),TrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }

    override fun onResume() {
        super.onResume()
        binding.mapView?.onResume()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView?.onStop()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView?.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }



}