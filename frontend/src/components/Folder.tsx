import { useState } from 'react';
import { motion } from 'motion/react';

type Variant = 'dark' | 'light';

const themes = {
  dark: {
    backFill: '#0d0d0d', backEdge: '#2e2e2e',
    flapFill: '#181818', flapFillOpacity: 0.92, flapStroke: '#4a4a4a',
    cardFill: '#f4f3ee', cardStroke: '#d8d6cb', cardLineFill: '#c6c3b6'
  },
  light: {
    backFill: '#ffffff', backEdge: '#d0d0d0',
    flapFill: '#f4f4f4', flapFillOpacity: 0.92, flapStroke: '#c4c4c4',
    cardFill: '#141414', cardStroke: '#2c2c2c', cardLineFill: '#4c4c4c'
  }
} as const;

const W = 321;
const H = 270;
const FLAP_H = 241; // bottom hinge sits flush with the folder base
const FLAP =
  'M0 25C0 11.1929 11.1929 0 25 0H136.084C143.044 0 149.689 2.90139 154.42 8.00608L178.08 33.5343C182.811 38.639 189.456 41.5404 196.416 41.5404H296C309.807 41.5404 321 52.7333 321 66.5404V216C321 229.807 309.807 241 296 241H25C11.1929 241 0 229.807 0 216V25Z';

function Document({ theme, index }: { theme: (typeof themes)[keyof typeof themes]; index: number }) {
  return (
    <svg width="148" height="196" viewBox="0 0 148 196" fill="none" aria-hidden style={{ display: 'block' }}>
      <rect x="1" y="1" width="146" height="194" rx="10" fill={theme.cardFill} stroke={theme.cardStroke} strokeOpacity="0.6" />
      <rect x="13" y="20" width="122" height="7" rx="3.5" fill={theme.cardLineFill} opacity={index === 0 ? 1 : 0.6} />
      {Array.from({ length: 6 }).map((_, i) => (
        <rect key={i} x="13" y={38 + i * 24} width={108 - (i % 2) * 24} height="4" rx="2" fill={theme.cardLineFill} opacity={0.35 + (6 - i) / 9} />
      ))}
    </svg>
  );
}

interface FolderProps { variant?: Variant; open?: boolean; }

export function Folder({ variant = 'dark', open = false }: FolderProps) {
  const theme = themes[variant];
  const [hover, setHover] = useState(false);
  const active = open || hover;

  return (
    <div
      style={{ position: 'relative', width: W, height: H, perspective: 900, touchAction: 'manipulation' }}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      aria-hidden
    >
      {/* back panel, exactly the folder footprint */}
      <div style={{ position: 'absolute', inset: 0, borderRadius: 22, background: theme.backFill, border: `1px solid ${theme.backEdge}` }} />

      {/* papers, centered via flex so no translate is needed on the animated nodes */}
      <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <motion.div style={{ position: 'absolute' }}
          animate={active ? { x: 34, y: -8, rotate: 9 } : { x: 7, y: 0, rotate: 4 }}
          transition={{ type: 'spring', stiffness: 140, damping: 16 }}>
          <Document theme={theme} index={0} />
        </motion.div>
        <motion.div style={{ position: 'absolute' }}
          animate={active ? { x: 0, y: -18, rotate: -1 } : { x: 0, y: -6, rotate: 0 }}
          transition={{ type: 'spring', stiffness: 140, damping: 16 }}>
          <Document theme={theme} index={1} />
        </motion.div>
        <motion.div style={{ position: 'absolute' }}
          animate={active ? { x: -34, y: -8, rotate: -9 } : { x: -7, y: 0, rotate: -4 }}
          transition={{ type: 'spring', stiffness: 140, damping: 16 }}>
          <Document theme={theme} index={2} />
        </motion.div>
      </div>

      {/* front flap: anchored flush with the footprint; motion only rotates it */}
      <motion.div
        style={{ position: 'absolute', left: 0, top: H - FLAP_H, width: W, height: FLAP_H, zIndex: 4, pointerEvents: 'none', transformOrigin: '50% 100%', transformStyle: 'preserve-3d' }}
        initial={false}
        animate={{ rotateX: active ? -50 : 0 }}
        transition={{ type: 'spring', stiffness: 120, damping: 15 }}
      >
        <div style={{ position: 'absolute', inset: 0, backdropFilter: 'blur(6px)', WebkitBackdropFilter: 'blur(6px)', clipPath: `path('${FLAP}')`, WebkitClipPath: `path('${FLAP}')`, transform: 'translateZ(0)', willChange: 'transform' }} />
        <svg width={W} height={FLAP_H} viewBox="0 0 321 241" fill="none" style={{ position: 'absolute', inset: 0 }}>
          <path d={FLAP} fill={theme.flapFill} fillOpacity={theme.flapFillOpacity} />
          <path d={FLAP} stroke={theme.flapStroke} strokeWidth="1.3" />
        </svg>
      </motion.div>
    </div>
  );
}
