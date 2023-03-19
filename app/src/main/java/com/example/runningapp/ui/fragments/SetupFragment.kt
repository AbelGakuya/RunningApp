package com.example.runningapp.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.runningapp.R
import com.example.runningapp.databinding.FragmentSetupBinding

class SetupFragment:Fragment() {
    private lateinit var binding: FragmentSetupBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSetupBinding.inflate(inflater,container,false)
        val view = binding.root

        val tvContinue = binding.tvContinue
        tvContinue.setOnClickListener {
            findNavController().navigate(R.id.action_setupFragment_to_runFragment)
        }

        return view
    }
}