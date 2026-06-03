// hooks/useTts.ts
// 브라우저 기본 음성합성(speechSynthesis) 대신 서버(Typecast) 합성 오디오를 재생한다.
// 백엔드 POST /api/tts { text, voice } → audio/wav 바이트를 받아 <audio>로 직렬 재생.
// 키/보이스 미설정 시 백엔드가 503을 주므로 재생만 조용히 스킵된다(에러 노출 없음).
import { useCallback, useEffect, useRef, useState } from 'react';

export type TtsVoice = 'persona' | 'riding';

// Typecast text 상한(2000자)과 체감 지연을 고려해 문장 단위로 잘라 순차 재생한다.
const MAX_CHUNK = 1500;

function chunkText(text: string, maxLen = MAX_CHUNK): string[] {
    const normalized = text.replace(/\s+/g, ' ').trim();
    if (!normalized) return [];
    // 한국어/영문 문장부호 기준 분할(마침표·물음표·느낌표·…·。)
    const sentences = normalized.match(/[^.!?。…]+[.!?。…]?/g) ?? [normalized];
    const chunks: string[] = [];
    let cur = '';
    for (const s of sentences) {
        if (cur && (cur + s).length > maxLen) {
            chunks.push(cur.trim());
            cur = s;
        } else {
            cur += s;
        }
    }
    if (cur.trim()) chunks.push(cur.trim());
    return chunks;
}

export function useTts(voice: TtsVoice = 'persona') {
    const supported = typeof window !== 'undefined' && typeof Audio !== 'undefined';
    const [speaking, setSpeaking] = useState(false);

    const queueRef = useRef<string[]>([]);
    const audioRef = useRef<HTMLAudioElement | null>(null);
    const urlRef = useRef<string | null>(null);
    const playingRef = useRef(false);
    const stoppedRef = useRef(false);

    const revokeUrl = useCallback(() => {
        if (urlRef.current) {
            URL.revokeObjectURL(urlRef.current);
            urlRef.current = null;
        }
    }, []);

    const finish = useCallback(() => {
        playingRef.current = false;
        setSpeaking(false);
    }, []);

    const playNext = useCallback(async () => {
        if (stoppedRef.current) { finish(); return; }
        const text = queueRef.current.shift();
        if (!text) { finish(); return; }

        try {
            const res = await fetch('/api/tts', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text, voice }),
            });
            if (stoppedRef.current) { finish(); return; }
            if (!res.ok) { playNext(); return; } // 한 청크 실패해도 다음 청크 진행

            const blob = await res.blob();
            if (stoppedRef.current) { finish(); return; }

            revokeUrl();
            const url = URL.createObjectURL(blob);
            urlRef.current = url;
            const audio = new Audio(url);
            audioRef.current = audio;
            audio.onended = () => { revokeUrl(); playNext(); };
            audio.onerror = () => { revokeUrl(); playNext(); };
            await audio.play();
        } catch {
            playNext();
        }
    }, [voice, finish, revokeUrl]);

    const speak = useCallback((text: string) => {
        if (!supported || !text.trim()) return;
        stoppedRef.current = false;
        for (const c of chunkText(text)) queueRef.current.push(c);
        if (!playingRef.current && queueRef.current.length) {
            playingRef.current = true;
            setSpeaking(true);
            playNext();
        }
    }, [supported, playNext]);

    const stop = useCallback(() => {
        stoppedRef.current = true;
        queueRef.current = [];
        if (audioRef.current) {
            audioRef.current.pause();
            audioRef.current = null;
        }
        revokeUrl();
        finish();
    }, [revokeUrl, finish]);

    // 언마운트 시 정리
    useEffect(() => () => {
        stoppedRef.current = true;
        if (audioRef.current) audioRef.current.pause();
        revokeUrl();
    }, [revokeUrl]);

    return { supported, speaking, speak, stop };
}