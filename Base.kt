abstract class BaseFragment : Fragment() {
    companion object {
        private var mLastClickTime = 0L
    }

    private var toast: Toast? = null


    // quản lý audio
    private var mAudioManager: AudioManager? = null
    private var mTelephonyManager: TelephonyManager? = null
    private lateinit var request: AudioFocusRequest


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutId(), container, false)
    }

    abstract fun getLayoutId(): Int

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (null != activity) {
            //Lấy audio manager và telephone manager
            mAudioManager =
                requireActivity().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            mTelephonyManager =
                requireActivity().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        }
        try {
            initViews(savedInstanceState)
        } catch (e: Exception) {
            findNavController().popBackStack()
        }
        addEvents()
    }


    fun blockMultiTouch(milisecs: Long = 600L): Boolean {
        // mis-clicking prevention, using threshold of 200 ms
        if (SystemClock.elapsedRealtime() - mLastClickTime < milisecs) {
            if (context != null) {
                showToast(requireContext(), getString(R.string.text_multi_touch))
            }
            return true
        }
        mLastClickTime = SystemClock.elapsedRealtime()
        killToast()

        return false
    }

    /**
     * Set màu cho thanh status bar, state = true: màu chữ của thanh status bar là màu tối
     */
    protected fun setStatusbarColor(color: Int = Color.BLACK, state: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val window = activity?.window
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)


                val newUiVisibility = window.decorView.systemUiVisibility

                window.decorView.systemUiVisibility = if (state) {
                    //Dark Text to show up on your light status bar
                    newUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

                } else {
                    //Light Text to show up on your dark status bar
                    newUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                window.statusBarColor = color
//                ObjectAnimator.ofArgb(window, "statusBarColor", window.statusBarColor, color).start()
            }
        }
    }

    /**
     * Nơi các view xử lý sự kiện
     */
    private fun addEvents() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPressed()
                }
            })
        initEvents()
    }

    /**
     * Nơi xử lý xự kiện cho các view ở các fragment kế thừa
     */
    abstract fun initEvents()

    /**
     * Khởi tạo view ở các fragment kế thừa
     */
    abstract fun initViews(savedInstanceState: Bundle?)

    /**
     * Xử lý sự kiến nhấn back ở các fragment kế thừa
     */
    abstract fun handleBackPressed()

    /**
     * Hàm show thông báo chung cho các fragment kế thừa
     */
    fun showToast(context: Context, message: String) {
        when {
            toast == null -> {
                // Create toast if found null, it would he the case of first call only
                toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            }
            toast!!.view == null -> {
                // Toast not showing, so create new one
                toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            }
            else -> {
                // Updating toast message is showing
                toast!!.setText(message)
            }
        }
        toast!!.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, 0)
        // Showing toast finally
        toast!!.show()
    }

    /**
     * Xóa toast thông báo
     */
    fun killToast() {
        toast?.cancel()
        toast = null
    }


    /**
     * Kiểm tra quyền truy cập bộ nhớ
     */
    protected fun haveStoragePermission() =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PERMISSION_GRANTED

    /**
     * Kiểm tra quyền truy cập camera
     */
    protected fun haveCameraPermission() =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PERMISSION_GRANTED

    /**
     * Ngừng lắng nghe tín hiệu media
     */
    fun stopRequestAudio() {
        if (mAudioManager != null) {
            abandonMediaFocus()
        }
    }

    /**
     * Yêu cầu ngừng media các app khác
     */
    fun requestAudio(): Boolean {
        return mAudioManager != null && requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Gửi tín hiệu audio bị mất hay không cho các fragment lằng nghe
     */
    open fun isAudioLoss(state: Boolean) {}

    /**
     * Tắt âm thanh từ app khác & bắt sự kiện âm thanh
     */
    private fun requestAudioFocus(): Int {
        abandonMediaFocus()
        if (mTelephonyManager != null) {
            mTelephonyManager!!.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //
            request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(afListener)
                .build()
            //
            mAudioManager!!.requestAudioFocus(
                request
            )
        } else {
            @Suppress("DEPRECATION")
            mAudioManager!!.requestAudioFocus(
                afListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    /**
     * Ngừng bắt sự kiện âm thanh
     */
    private fun abandonMediaFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ::request.isInitialized) {
            mAudioManager!!.abandonAudioFocusRequest(request)
        } else {
            @Suppress("DEPRECATION")
            mAudioManager!!.abandonAudioFocus(afListener)
        }
        if (mTelephonyManager != null)
            mTelephonyManager!!.listen(phoneListener, PhoneStateListener.LISTEN_NONE)
    }

    /**
     * Xử lý khi video được khôi phục hoặc bị mất
     */
    private val afListener = AudioManager.OnAudioFocusChangeListener { i: Int ->
        when (i) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                //Âm thanh đã được khôi phục trở lại
                // -> có thể play media
                isAudioLoss(false)
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                //Tạm thời mất âm thanh, vẫn có thể giảm âm lượng và play hoặc tạm ngưng âm thanh
                //lowerVolume() ||  pause()
                isAudioLoss(true)
            }
        }
    }


    /**
     * Xử lý khi có cuộc gọi
     */
    private val phoneListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            if (state != TelephonyManager.CALL_STATE_IDLE) {
                isAudioLoss(true)
            }
            super.onCallStateChanged(state, phoneNumber)
        }
    }

}
