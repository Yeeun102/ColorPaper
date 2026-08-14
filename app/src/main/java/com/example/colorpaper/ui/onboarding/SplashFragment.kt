import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.example.colorpaper.R
import com.example.colorpaper.ui.login.LoginFragment
import com.example.colorpaper.ui.theme.ThemeManager
import com.example.colorpaper.MainActivity
import com.google.firebase.auth.FirebaseAuth

class SplashFragment : Fragment() {
    private val handler = Handler(Looper.getMainLooper())
    private var navigationFinished = false
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().findViewById<View>(R.id.bottom_navigation_bar)?.visibility = View.GONE

        val ivLogo = view.findViewById<ImageView>(R.id.iv_splash_logo)

        // 1. 현재 유저 테마의 팔레트 정보 싹 다 가져오기
        val palette = ThemeManager.currentPalette(requireContext())

        // 2. 팔레트에서 로고 파일 쏙 빼서 이미지뷰에 박아주기
        ivLogo.setImageResource(palette.toolbarLogo)

        view.setBackgroundResource(palette.screenBackground)

        // 3. 페이드인 애니메이션
        val fadeIn = ObjectAnimator.ofFloat(ivLogo, "alpha", 0f, 1f)
        fadeIn.duration = 500 // 0.5초
        fadeIn.start()

        // 4. 1.5초 뒤에 다음 화면으로 이동
        handler.postDelayed(::resolveStartDestination, SPLASH_DURATION_MILLIS)
    }

    private fun resolveStartDestination() {
        if (!isAdded || navigationFinished) return
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            openHome(auth)
            return
        }

        // Firebase가 디스크에 저장된 세션을 복원하는 동안 최초 currentUser가 잠시 null일 수 있다.
        authStateListener = FirebaseAuth.AuthStateListener { updatedAuth ->
            if (updatedAuth.currentUser != null) openHome(updatedAuth)
        }.also(auth::addAuthStateListener)
        handler.postDelayed({
            if (!navigationFinished && isAdded) openLogin(auth)
        }, AUTH_RESTORE_TIMEOUT_MILLIS)
    }

    private fun openHome(auth: FirebaseAuth) {
        if (!isAdded || navigationFinished) return
        navigationFinished = true
        removeAuthListener(auth)
        (activity as? MainActivity)?.openHome()
    }

    private fun openLogin(auth: FirebaseAuth) {
        if (!isAdded || navigationFinished) return
        navigationFinished = true
        removeAuthListener(auth)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commitAllowingStateLoss()
    }

    private fun removeAuthListener(auth: FirebaseAuth = FirebaseAuth.getInstance()) {
        authStateListener?.let(auth::removeAuthStateListener)
        authStateListener = null
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        removeAuthListener()
        super.onDestroyView()
    }

    private companion object {
        const val SPLASH_DURATION_MILLIS = 1_500L
        const val AUTH_RESTORE_TIMEOUT_MILLIS = 1_500L
    }
}
