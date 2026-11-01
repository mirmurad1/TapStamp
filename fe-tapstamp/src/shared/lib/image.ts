/**
 * Yükləməzdən əvvəl şəkli telefonda/brauzerdə hazırlayır: istənilən formatı (iPhone-un HEIC-i, JPEG, PNG, WebP)
 * açır, uzun tərəfi {@code maxSide} piksələ qədər kiçildir (fotonun istiqaməti nəzərə alınır) və JPEG-ə çevirir.
 * iPhone fotoşəkli belə 3–6 MB-dan ~200–400 KB-a düşür və serverin PNG/JPEG, 2 MB həddinə uyğun olur.
 *
 * Brauzer şəkli aça bilmirsə (məs. HEIC iPhone-dan kənarda) və ya şəkil çox kiçikdirsə — {@code null}.
 */
export async function prepareImage(file: File, { maxSide = 1600, minSide = 200, quality = 0.85 } = {}): Promise<File | null> {
  const source = await decode(file);
  if (!source) {
    return null;
  }
  try {
    const { width, height } = source;
    if (Math.min(width, height) < minSide) {
      return null;
    }
    const scale = Math.min(1, maxSide / Math.max(width, height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(width * scale);
    canvas.height = Math.round(height * scale);
    const context = canvas.getContext('2d');
    if (!context) {
      return null;
    }
    // Şəffaf PNG JPEG-də qara olmasın — ağ fon
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(source, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
    if (!blob) {
      return null;
    }
    const name = `${file.name.replace(/\.[^.]+$/, '') || 'image'}.jpg`;
    return new File([blob], name, { type: 'image/jpeg' });
  } finally {
    if ('close' in source) {
      source.close();
    }
  }
}

/** Şəkli açır: əvvəlcə createImageBitmap (EXIF istiqaməti ilə), alınmasa <img> vasitəsilə. */
async function decode(file: File): Promise<ImageBitmap | HTMLImageElement | null> {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch {
      // Bəzi brauzerlər bu formatı createImageBitmap ilə açmır — <img> ilə cəhd edilir
    }
  }
  const url = URL.createObjectURL(file);
  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    return image.naturalWidth > 0 ? image : null;
  } catch {
    return null;
  } finally {
    URL.revokeObjectURL(url);
  }
}
