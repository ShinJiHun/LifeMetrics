// hooks/useTts.ts
import { useCallback, useEffect, useRef, useState } from 'react';

export function useTts(lang = 'ko-KR') {
    const supported = typeof window !== 'undefined' && 'speechSynthesis' in window;
    const [speaking, setSpeaking] = useState(false);
    const voiceRef = useRef<SpeechSynthesisVoice | null>(null);
    const queueRef = useRef<string[]>([]);

    // voice는 비동기 로드 → voiceschanged 한 번 기다려야 첫 발화가 안정적
    useEffect(() => {
        if (!supported) return;
        const pick = () => {
            const voices = window.speechSynthesis.getVoices();
            voiceRef.current =
                voices.find(v => v.lang === lang) ??
                voices.find(v => v.lang.startsWith('ko')) ?? null;
        };
        pick();
        window.speechSynthesis.addEventListener('voiceschanged', pick);
        return () => window.speechSynthesis.removeEventListener('voiceschanged', pick);
    }, [supported, lang]);

    const playNext = useCallback(() => {
        const text = queueRef.current.shift();
        if (!text) { setSpeaking(false); return; }
        const u = new SpeechSynthesisUtterance(text);
        if (voiceRef.current) u.voice = voiceRef.current;
        u.lang = lang;
        u.rate = 1.0;
        u.onend = playNext;     // 다음 문장 직렬 재생
        u.onerror = playNext;
        window.speechSynthesis.speak(u);
    }, [lang]);

    const speak = useCallback((text: string) => {
        if (!supported || !text.trim()) return;
        queueRef.current.push(text.trim());
        if (!window.speechSynthesis.speaking) { setSpeaking(true); playNext(); }
    }, [supported, playNext]);

    const stop = useCallback(() => {
        queueRef.current = [];
        window.speechSynthesis.cancel();
        setSpeaking(false);
    }, []);

    return { supported, speaking, speak, stop };
}
