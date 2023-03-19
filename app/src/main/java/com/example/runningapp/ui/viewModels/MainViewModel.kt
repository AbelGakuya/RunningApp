package com.example.runningapp.ui.viewModels

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import com.example.runningapp.repositories.MainRepository

class MainViewModel @ViewModelInject constructor(
    val repository: MainRepository
) : ViewModel() {
}