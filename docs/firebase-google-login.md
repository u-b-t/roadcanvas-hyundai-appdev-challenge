# Firebase Google 로그인 설정

Firebase 프로젝트 `roadcanvas-f9f2a`의 Authentication에서 Google 제공자를 활성화했다. 저장소 루트 `firebase.json`의 `auth.providers`에 Google 제공자와 익명 인증이 함께 있으며 `npx -y firebase-tools@latest deploy --only auth --project <프로젝트 ID>`로 재적용할 수 있다. 한 제공자만 담은 별도 설정으로 배포하지 않는다. Google 제공자의 지원 이메일은 CLI 기본값이며 실제 문의를 받을 수 있는지 확인 후 운영용 주소로 변경해야 한다.

Android 앱 `kr.roadcanvas.app`에 이 개발 환경의 debug 서명 SHA-1을 등록했다. 기기나 빌드 서버가 다른 debug 키를 쓰면 해당 지문을 추가해야 하며, 배포 빌드에는 실제 서명 인증서의 SHA-1을 별도로 등록해야 한다. 서명 키 자체는 공유하거나 저장소에 추가하지 않는다. Firebase 콘솔에서 Android 앱의 업데이트된 `google-services.json`을 받아 `app/`에 둔다. Google 제공자 활성화 및 SHA 등록 후 받은 파일에는 OAuth 클라이언트 구성이 포함된다.

클라이언트는 Firebase Authentication과 Google ID 토큰을 사용해야 하며, 정차 상태에서 로그인과 로그아웃을 제공하고 실패 및 미지원 상태를 안내해야 한다. Google의 Android 문서는 Credential Manager와 Google 로그인 제공자를 안내한다. 실제 구현 전에 대상 Pleos 환경에서 사용 가능한 인증 수단과 플랫폼 제한을 확인한다. 로컬 Pleos Connect Emulator v2.1.0에서는 `com.google.android.gms`, `com.google.android.gsf`, `com.android.vending` 패키지가 검색되지 않았다. 따라서 Google Play 서비스 기반 로그인 동작은 확인되지 않았으며 이 설정만으로 차량 로그인이나 실제 Google 계정 인증이 동작한다고 볼 수 없다. Play 서비스가 없는 환경은 미지원 안내나 별도 인증 방식을 설계해야 한다.

참고: https://firebase.google.com/docs/auth/android/google-signin
