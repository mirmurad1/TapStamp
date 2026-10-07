import { useEffect, useState } from 'react';

/**
 * "Nömrənizi bir daha soruşmayacağıq" izahı yalnız telefon nömrəsi ilə girişdən dərhal sonrakı ekranda göstərilir.
 * İşarə yalnız yaddaşdadır: səhifə yenilənəndə və ya növbəti səhifəyə keçəndə izah görünmür.
 */
let justSignedIn = false;

export function markJustSignedIn(): void {
  justSignedIn = true;
}

/** İlk render-də işarəni oxuyur, komponent ekrandan gedəndə sıfırlayır (StrictMode-da da bir dəfə göstərilir). */
export function useJustSignedIn(): boolean {
  const [value] = useState(() => justSignedIn);
  useEffect(
    () => () => {
      justSignedIn = false;
    },
    [],
  );
  return value;
}
