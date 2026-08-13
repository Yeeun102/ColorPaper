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

class SplashFragment : Fragment() {

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
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded) {
                parentFragmentManager.beginTransaction().replace(R.id.fragment_container,
                    LoginFragment()
                ).commitAllowingStateLoss()
            }
        }, 1500)
    }
}