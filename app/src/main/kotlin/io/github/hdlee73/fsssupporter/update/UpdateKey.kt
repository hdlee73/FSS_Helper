package io.github.hdlee73.fsssupporter.update

/**
 * 업데이트 서명 확인용 공개키(Ed25519, X.509 SubjectPublicKeyInfo, base64).
 * 짝이 되는 개인키는 저장소 주인의 GitHub Secret `UPDATE_SIGNING_KEY`에만 있다.
 */
object UpdateKey {
    const val PUBLIC_KEY = "MCowBQYDK2VwAyEAkcHa72rRFA2gBicD/42bGOsmIRYbFWPG5oXEl/RWQ+w="
}
