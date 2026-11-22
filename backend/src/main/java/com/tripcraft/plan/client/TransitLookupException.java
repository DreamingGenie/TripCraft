package com.tripcraft.plan.client;

/**
 * 외부 경로 API(ODsay·TMap) 호출이 실패했다 — 키 오류·호출 한도·네트워크·서버 오류 등.
 * "경로가 없다"(NONE)와 구분하려고 둔다. NONE 은 캐시해도 되지만 실패는 다음에 다시 물어야 한다.
 */
public class TransitLookupException extends RuntimeException {
    public TransitLookupException(String message) {
        super(message);
    }

    public TransitLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
