abstract class BaseFragment : Fragment() {
   
    // quản lý audio
    private var mAudioManager: AudioManager? = null
    private var mTelephonyManager: TelephonyManager? = null
    private lateinit var request: AudioFocusRequest

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



# Các class con:
     /**
     * Play hoặc pause video nếu có cuộc gọi hoặc nhạc từ app khác
     */
    override fun isAudioLoss(state: Boolean) {
        super.isAudioLoss(state)
        if (state) {
            // Mất âm thanh, pause media
        } else {
            // Có lại âm thanh, resume media
        }
    }
Khi play media gọi hàm này trước khi play: requestAudio()
Khi pause gọi hàm này: stopRequestAudio()
