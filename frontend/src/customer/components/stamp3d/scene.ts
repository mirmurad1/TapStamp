import {
  ACESFilmicToneMapping,
  CanvasTexture,
  Color,
  CylinderGeometry,
  DirectionalLight,
  Group,
  HemisphereLight,
  Mesh,
  MeshBasicMaterial,
  MeshPhysicalMaterial,
  MeshStandardMaterial,
  PCFSoftShadowMap,
  PerspectiveCamera,
  PlaneGeometry,
  RingGeometry,
  Scene,
  ShadowMaterial,
  SphereGeometry,
  SRGBColorSpace,
  TorusGeometry,
  WebGLRenderer,
} from 'three';

/**
 * Möhür ekranının real 3D səhnəsi (Three.js / WebGL). Bu modul yalnız möhür ekranında dinamik yüklənir (ayrıca
 * paket hissəsi), əsas tətbiqin ölçüsünə təsir etmir.
 *
 * Damğa şirkətin brend rəngindədir (CSS dəyişənləri --primary və --accent). Ssenari (~2,4 san): damğa havada
 * yellənir, qalxır, zərblə masaya vurulur (sıxılır, kamera silkələnir, mürəkkəb dalğası), fırlanaraq kiçilib yox olur.
 * Masada rəngli möhür izi qalır.
 */
export interface StampSceneOptions {
  reducedMotion: boolean;
  onImpact: () => void;
}

const DURATION = 2.4;
const IMPACT = 1.25;

export function startStampScene(canvas: HTMLCanvasElement, options: StampSceneOptions): () => void {
  const styles = getComputedStyle(document.documentElement);
  const primary = new Color(cssColor(styles.getPropertyValue('--primary'), '#6d28d9'));
  const accent = new Color(cssColor(styles.getPropertyValue('--accent'), '#ec4899'));

  const renderer = new WebGLRenderer({ canvas, alpha: true, antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(canvas.clientWidth, canvas.clientHeight, false);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = PCFSoftShadowMap;
  renderer.toneMapping = ACESFilmicToneMapping;
  renderer.outputColorSpace = SRGBColorSpace;

  const scene = new Scene();
  const camera = new PerspectiveCamera(34, canvas.clientWidth / canvas.clientHeight, 0.1, 50);
  const cameraBase = { x: 0, y: 7, z: 11 };
  camera.position.set(cameraBase.x, cameraBase.y, cameraBase.z);
  camera.lookAt(0, 1.8, 0);

  scene.add(new HemisphereLight(0xffffff, 0x444466, 1.4));
  const sun = new DirectionalLight(0xffffff, 2.6);
  sun.position.set(1, 10, 1.5);
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 1024);
  sun.shadow.camera.left = -4;
  sun.shadow.camera.right = 4;
  sun.shadow.camera.top = 4;
  sun.shadow.camera.bottom = -4;
  sun.shadow.radius = 6;
  scene.add(sun);
  const rim = new DirectionalLight(accent, 1.2);
  rim.position.set(-4, 3, -3);
  scene.add(rim);

  // Masa: yalnız kölgəni göstərir (fon şəffafdır — səhifənin rəngi görünür)
  const ground = new Mesh(new PlaneGeometry(14, 14), new ShadowMaterial({ opacity: 0.16 }));
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  scene.add(ground);

  // Masada qalan möhür izi və mürəkkəb dalğası
  const imprintTexture = new CanvasTexture(drawImprint(`#${primary.getHexString()}`));
  imprintTexture.colorSpace = SRGBColorSpace;
  const imprintMaterial = new MeshBasicMaterial({ map: imprintTexture, transparent: true, opacity: 0, depthWrite: false });
  const imprint = new Mesh(new PlaneGeometry(2.6, 2.6), imprintMaterial);
  imprint.rotation.set(-Math.PI / 2, 0, -0.25);
  imprint.position.y = 0.005;
  scene.add(imprint);
  const inkMaterial = new MeshBasicMaterial({ color: primary, transparent: true, opacity: 0, depthWrite: false });
  const ink = new Mesh(new RingGeometry(1.05, 1.18, 64), inkMaterial);
  ink.rotation.x = -Math.PI / 2;
  ink.position.y = 0.01;
  scene.add(ink);

  // Damğa: rezin altlıq, parlaq gövdə, metal halqa, boyun, kürə tutacaq
  const stamp = new Group();
  const glossy = (color: Color) =>
    new MeshPhysicalMaterial({ color, roughness: 0.28, metalness: 0.05, clearcoat: 1, clearcoatRoughness: 0.15 });
  const parts: Mesh[] = [
    placed(new Mesh(new CylinderGeometry(1.15, 1.15, 0.18, 64), new MeshStandardMaterial({ color: 0x1d1d28, roughness: 0.9 })), 0.09),
    placed(new Mesh(new CylinderGeometry(1.02, 1.1, 0.55, 64), glossy(accent)), 0.455),
    placed(new Mesh(new TorusGeometry(1.03, 0.055, 16, 64), new MeshStandardMaterial({ color: 0xdadae6, metalness: 1, roughness: 0.3 })), 0.73, true),
    placed(new Mesh(new CylinderGeometry(0.2, 0.3, 0.75, 32), glossy(primary)), 1.1),
    placed(new Mesh(new SphereGeometry(0.55, 48, 32), glossy(primary)), 1.88),
  ];
  parts.forEach((part) => {
    part.castShadow = true;
    stamp.add(part);
  });
  scene.add(stamp);

  let frame = 0;
  let impacted = false;
  const start = performance.now();
  const render = (time: number) => {
    const t = options.reducedMotion ? DURATION : Math.min((time - start) / 1000, DURATION);
    pose(t, stamp);
    const sinceImpact = t - IMPACT;
    if (sinceImpact >= 0 && !impacted) {
      impacted = true;
      options.onImpact();
    }
    imprintMaterial.opacity = sinceImpact < 0 ? 0 : Math.min(sinceImpact / 0.12, 1) * 0.95;
    const wave = sinceImpact < 0 ? -1 : sinceImpact / 0.65;
    inkMaterial.opacity = wave >= 0 && wave <= 1 ? 0.55 * (1 - wave) : 0;
    ink.scale.setScalar(1 + Math.max(wave, 0) * 0.8);
    // Zərbə anında kamera silkələnir
    const shake = sinceImpact >= 0 && sinceImpact < 0.22 ? (1 - sinceImpact / 0.22) * 0.06 : 0;
    camera.position.set(cameraBase.x + Math.sin(t * 90) * shake, cameraBase.y + Math.cos(t * 75) * shake, cameraBase.z);
    camera.lookAt(0, 1.8, 0);
    renderer.render(scene, camera);
    if (t < DURATION) {
      frame = requestAnimationFrame(render);
    }
  };
  frame = requestAnimationFrame(render);

  return () => {
    cancelAnimationFrame(frame);
    scene.traverse((object) => {
      if (object instanceof Mesh) {
        object.geometry.dispose();
        (Array.isArray(object.material) ? object.material : [object.material]).forEach((material) => material.dispose());
      }
    });
    imprintTexture.dispose();
    renderer.dispose();
  };
}

function placed(mesh: Mesh, y: number, flat = false): Mesh {
  mesh.position.y = y;
  if (flat) {
    mesh.rotation.x = Math.PI / 2;
  }
  return mesh;
}

/** Damğanın vəziyyəti zamana görə: yellənmə → qalxma → zərbə (sıxılma) → fırlanaraq kiçilib yox olma. */
function pose(t: number, stamp: Group) {
  const lerp = (a: number, b: number, k: number) => a + (b - a) * k;
  const easeIn = (k: number) => k * k * k;
  const easeOut = (k: number) => 1 - (1 - k) ** 3;
  let y: number;
  let tilt: number;
  let spin = 0;
  let scale = 1;
  let squash = 1;
  if (t < 0.35) {
    const k = easeOut(t / 0.35);
    y = lerp(2.6, 2.1, k);
    tilt = -0.35;
    scale = lerp(0.4, 1, k);
  } else if (t < 1.0) {
    const k = (t - 0.35) / 0.65;
    y = lerp(2.1, 2.5, easeOut(k)) + Math.sin(k * Math.PI * 2) * 0.06;
    tilt = lerp(-0.35, -0.22, k);
  } else if (t < IMPACT) {
    const k = easeIn((t - 1.0) / (IMPACT - 1.0));
    y = lerp(2.5, 0, k);
    tilt = lerp(-0.22, 0, k);
  } else if (t < 1.6) {
    const k = (t - IMPACT) / (1.6 - IMPACT);
    y = 0;
    tilt = 0;
    squash = 1 - Math.sin(Math.min(k * 2, 1) * Math.PI) * 0.12;
  } else {
    const k = easeIn((t - 1.6) / (DURATION - 1.6));
    y = lerp(0, 2.2, k);
    tilt = lerp(0, 0.3, k);
    spin = lerp(0, 2.2, k);
    scale = Math.max(lerp(1, 0, k), 0.001);
  }
  stamp.position.y = y;
  stamp.rotation.set(tilt * 0.6, spin, tilt);
  stamp.scale.set(scale * (2 - squash), scale * squash, scale * (2 - squash));
}

/** Möhür izi: qalın halqa, qırıq daxili halqa və ulduz; kənarlarda bir az "mürəkkəb" tozu. */
function drawImprint(color: string): HTMLCanvasElement {
  const size = 512;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return canvas;
  }
  const c = size / 2;
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.lineWidth = 26;
  ctx.beginPath();
  ctx.arc(c, c, 220, 0, Math.PI * 2);
  ctx.stroke();
  ctx.lineWidth = 8;
  ctx.setLineDash([26, 18]);
  ctx.beginPath();
  ctx.arc(c, c, 175, 0, Math.PI * 2);
  ctx.stroke();
  ctx.setLineDash([]);
  ctx.beginPath();
  for (let i = 0; i < 10; i++) {
    const radius = i % 2 === 0 ? 120 : 52;
    const angle = (Math.PI / 5) * i - Math.PI / 2;
    ctx.lineTo(c + Math.cos(angle) * radius, c + Math.sin(angle) * radius);
  }
  ctx.closePath();
  ctx.fill();
  // Mürəkkəbin qeyri-bərabərliyi: kiçik boşluqlar
  ctx.globalCompositeOperation = 'destination-out';
  for (let i = 0; i < 260; i++) {
    ctx.globalAlpha = Math.random() * 0.5;
    ctx.beginPath();
    ctx.arc(Math.random() * size, Math.random() * size, Math.random() * 4 + 1, 0, Math.PI * 2);
    ctx.fill();
  }
  return canvas;
}

function cssColor(value: string, fallback: string): string {
  const trimmed = value.trim();
  return /^#[0-9a-f]{3,8}$/i.test(trimmed) ? trimmed : fallback;
}
