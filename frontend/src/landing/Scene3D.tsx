import { Canvas, useFrame } from '@react-three/fiber';
import { useLayoutEffect, useMemo, useRef } from 'react';
import * as THREE from 'three';

/**
 * One coherent visual language for the whole page: translucent layered forms, thin luminous lines, flowing cyan ribbons,
 * and small orbiting "units of money". Loaded lazily (this file is code-split) and paused when off screen.
 */
export interface Palette { core: string; accent: string; live: string; pink: string; green: string; line: string }
export const DARK: Palette = { core: '#8b5cf6', accent: '#a78bfa', live: '#22d3ee', pink: '#f472b6', green: '#34d399', line: '#c4b5fd' };
export const LIGHT: Palette = { core: '#6d28d9', accent: '#7c3aed', live: '#0891b2', pink: '#db2777', green: '#059669', line: '#5b21b6' };

function Lights({ c }: { c: Palette }) {
  return (<><ambientLight intensity={0.55} /><pointLight position={[4, 3, 4]} intensity={40} color={c.accent} /><pointLight position={[-4, -2, 3]} intensity={26} color={c.live} /></>);
}

function HeroObject({ c, small, animate }: { c: Palette; small: boolean; animate: boolean }) {
  const group = useRef<THREE.Group>(null);
  const ribbon = useRef<THREE.Mesh>(null);
  const curve = useMemo(() => new THREE.CatmullRomCurve3(Array.from({ length: 9 }, (_, i) => new THREE.Vector3(Math.cos(i * 0.9) * (1.8 + (i % 3) * 0.12), Math.sin(i * 1.3) * 0.7, Math.sin(i * 0.9) * (1.8 + (i % 2) * 0.1))), true), []);
  const particles = useMemo(() => {
    const n = small ? 140 : 380, a = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) { const r = 2.2 + ((i * 97) % 100) / 60, t = i * 2.399963, y = ((i * 53) % 100) / 100 * 3 - 1.5; a[i * 3] = Math.cos(t) * r; a[i * 3 + 1] = y; a[i * 3 + 2] = Math.sin(t) * r; }
    return a;
  }, [small]);
  useFrame((_, dt) => {
    if (!animate) return;
    if (group.current) { group.current.rotation.y += dt * 0.18; group.current.rotation.x = Math.sin(performance.now() / 4000) * 0.12; }
    if (ribbon.current) ribbon.current.rotation.y -= dt * 0.35;
  });
  return (
    <group ref={group}>
      <mesh><torusKnotGeometry args={[0.95, 0.26, small ? 90 : 180, small ? 10 : 18]} /><meshStandardMaterial color={c.core} metalness={0.85} roughness={0.18} transparent opacity={0.55} /></mesh>
      <mesh scale={1.04}><torusKnotGeometry args={[0.95, 0.26, small ? 50 : 90, 6]} /><meshBasicMaterial color={c.line} wireframe transparent opacity={0.22} /></mesh>
      <mesh><icosahedronGeometry args={[0.38, 2]} /><meshStandardMaterial color={c.live} emissive={c.live} emissiveIntensity={1.6} /></mesh>
      <mesh ref={ribbon}><tubeGeometry args={[curve, small ? 90 : 200, 0.035, 8, true]} /><meshStandardMaterial color={c.live} emissive={c.live} emissiveIntensity={1.2} /></mesh>
      <points><bufferGeometry><bufferAttribute attach="attributes-position" args={[particles, 3]} /></bufferGeometry><pointsMaterial color={c.line} size={0.03} sizeAttenuation transparent opacity={0.8} /></points>
    </group>
  );
}

const RINGS = [{ r: 1.9, n: 9, tilt: 0.35, color: 'accent', speed: 0.16 }, { r: 2.6, n: 12, tilt: -0.55, color: 'live', speed: -0.11 }, { r: 3.3, n: 14, tilt: 0.9, color: 'pink', speed: 0.08 }, { r: 4.0, n: 16, tilt: -0.2, color: 'green', speed: -0.06 }] as const;

function Ring({ r, n, tilt, color, speed, c, animate }: { r: number; n: number; tilt: number; color: keyof Palette; speed: number; c: Palette; animate: boolean }) {
  const mesh = useRef<THREE.InstancedMesh>(null);
  const holder = useRef<THREE.Group>(null);
  useLayoutEffect(() => {
    const m = mesh.current; if (!m) return;
    const o = new THREE.Object3D();
    for (let i = 0; i < n; i++) { const a = (i / n) * Math.PI * 2; o.position.set(Math.cos(a) * r, 0, Math.sin(a) * r); o.scale.setScalar(0.07 + ((i * 37) % 10) / 120); o.updateMatrix(); m.setMatrixAt(i, o.matrix); }
    m.instanceMatrix.needsUpdate = true;
  }, [n, r]);
  useFrame((_, dt) => { if (animate && holder.current) holder.current.rotation.y += dt * speed; });
  return (
    <group rotation={[tilt, 0, 0]}><group ref={holder}>
      <mesh rotation={[Math.PI / 2, 0, 0]}><ringGeometry args={[r - 0.005, r + 0.005, 128]} /><meshBasicMaterial color={c.line} transparent opacity={0.25} side={THREE.DoubleSide} /></mesh>
      <instancedMesh ref={mesh} args={[undefined, undefined, n]}><sphereGeometry args={[1, 12, 12]} /><meshStandardMaterial color={c[color]} emissive={c[color]} emissiveIntensity={0.9} /></instancedMesh>
    </group></group>
  );
}

function Universe({ c, progress, small, animate }: { c: Palette; progress: number; small: boolean; animate: boolean }) {
  const group = useRef<THREE.Group>(null);
  useFrame((state) => {
    // Scroll progress drives camera depth and orbit - the page itself keeps scrolling normally.
    const z = 10.5 - progress * 4.2;
    state.camera.position.z += (z - state.camera.position.z) * 0.08;
    state.camera.position.y += ((1.8 - progress * 1.6) - state.camera.position.y) * 0.08;
    state.camera.lookAt(0, 0, 0);
    if (group.current) group.current.rotation.y = progress * Math.PI * 1.4;
  });
  const rings = small ? RINGS.slice(0, 3) : RINGS;
  return (
    <group ref={group}>
      <mesh><icosahedronGeometry args={[0.7, 3]} /><meshStandardMaterial color={c.core} emissive={c.core} emissiveIntensity={0.9} metalness={0.4} roughness={0.3} /></mesh>
      <mesh scale={1.5}><icosahedronGeometry args={[0.7, 1]} /><meshBasicMaterial color={c.line} wireframe transparent opacity={0.18} /></mesh>
      {rings.map((rg) => <Ring key={rg.r} {...rg} c={c} animate={animate} />)}
    </group>
  );
}

interface Props { kind: 'hero' | 'universe'; palette: Palette; progress?: number; active: boolean; small: boolean; reduced: boolean; onLost: () => void }

export default function Scene3D({ kind, palette, progress = 0, active, small, reduced, onLost }: Props) {
  const animate = active && !reduced;
  return (
    <Canvas dpr={small ? 1 : [1, 1.5]} gl={{ antialias: !small, alpha: true, powerPreference: 'low-power' }} frameloop={animate || kind === 'universe' ? (active ? 'always' : 'never') : 'demand'}
      camera={{ position: kind === 'hero' ? [0, 0, 6] : [0, 1.8, 10.5], fov: kind === 'hero' ? 42 : 40 }} onCreated={({ gl }) => { gl.domElement.addEventListener('webglcontextlost', (e) => { e.preventDefault(); onLost(); }); }} aria-hidden="true">
      <Lights c={palette} />
      {kind === 'hero' ? <HeroObject c={palette} small={small} animate={animate} /> : <Universe c={palette} progress={progress} small={small} animate={animate} />}
    </Canvas>
  );
}
