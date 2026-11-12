// Claude 는 긴 변 1568px 를 넘는 이미지를 어차피 줄여서 본다 → 미리 줄여 보내도 인식 품질은 같다.
// 폰 원본 사진(수 MB)도 서버 이미지 한도(5MB) 안으로 들어오고 전송 시간도 준다.
const MAX_EDGE = 1568;

/** 이미지의 긴 변이 MAX_EDGE 를 넘거나 1.5MB 이상이면 JPEG 로 줄인다. PDF 등 이미지가 아니거나 실패하면 원본 그대로. */
export async function shrinkImage(file: File): Promise<File> {
    if (!file.type.startsWith("image/") && !/\.(jpe?g|png|webp|gif|heic|heif)$/i.test(file.name)) return file;
    try {
        const bmp = await createImageBitmap(file);
        const scale = Math.min(1, MAX_EDGE / Math.max(bmp.width, bmp.height));
        if (scale === 1 && file.size < 1.5 * 1024 * 1024) {
            bmp.close();
            return file;
        }
        const canvas = document.createElement("canvas");
        canvas.width = Math.round(bmp.width * scale);
        canvas.height = Math.round(bmp.height * scale);
        canvas.getContext("2d")!.drawImage(bmp, 0, 0, canvas.width, canvas.height);
        bmp.close();
        const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, "image/jpeg", 0.9));
        if (!blob) return file;
        return new File([blob], file.name.replace(/\.\w+$/, "") + ".jpg", {type: "image/jpeg"});
    } catch {
        return file;
    }
}
