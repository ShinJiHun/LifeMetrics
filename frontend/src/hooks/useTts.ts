import { useCallback, useEffect, useRef, useState } from 'react';

export type TtsVoice = 'persona' | 'riding';

const MAX_CHUNK = 1500;

function chunkText(text: string, maxLen = MAX_CHUNK): string[] {
    const normalized = text.replace(/\s+/g, ' ').trim();
    if (!normalized) return [];
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

    useEffect(() => () => {
        stoppedRef.current = true;
        if (audioRef.current) audioRef.current.pause();
        revokeUrl();
    }, [revokeUrl]);

    return { supported, speaking, speak, stop };
}