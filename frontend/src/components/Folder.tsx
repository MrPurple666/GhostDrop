import { useState } from 'react';
import { motion } from 'motion/react';

type Variant = 'dark' | 'light';

const themes = {
  // Ref "black": OLED folder, light papers, frosted front.
  dark: {
    backFill: '#0d0d0d',
    flapFill: '#1f1f1f',
    flapFillOpacity: 0.55,
    flapStroke: '#5a5a5a',
    cardFill: '#f4f3ee',
    cardStroke: '#d8d6cb',
    cardLineFill: '#c6c3b6'
  },
  // Ref "white": paper folder, dark papers.
  light: {
    backFill: '#ffffff',
    flapFill: '#f5f5f5',
    flapFillOpacity: 0.72,
    flapStroke: '#c9c9c9',
    cardFill: '#151515',
    cardStroke: '#2c2c2c',
    cardLineFill: '#4c4c4c'
  }
} as const;

const BASE_WIDTH = 321;
const BASE_HEIGHT = 270;

const FLAP_PATH =
  'M0 25C0 11.1929 11.1929 0 25 0H136.084C143.044 0 149.689 2.90139 154.42 8.00608L178.08 33.5343C182.811 38.639 189.456 41.5404 196.416 41.5404H296C309.807 41.5404 321 52.7333 321 66.5404V216C321 229.807 309.807 241 296 241H25C11.1929 241 0 229.807 0 216V25Z';

function DocumentCard({ theme }: { theme: (typeof themes)[keyof typeof themes] }) {
  return (
    <svg width="164" height="214" viewBox="0 0 164 214" fill="none" aria-hidden style={{ display: 'block' }}>
      <rect width="164" height="214" rx="20" fill={theme.cardFill} />
      <rect x="0.5" y="0.5" width="163" height="213" rx="19.5" stroke={theme.cardStroke} />
      <rect x="16" y="30" width="132" height="9" rx="4.5" fill={theme.cardLineFill} />
      {Array.from({ length: 6 }).map((_, i) => (
        <rect key={i} x="16" y={54 + i * 24} width={118 - (i % 2) * 26} height="5" rx="2.5" fill={theme.cardLineFill} opacity={1 - i / 7} />
      ))}
    </svg>
  );
}

interface FolderProps { variant?: Variant; open?: boolean; }

export function Folder({ variant = 'dark', open = false }: FolderProps) {
  const theme = themes[variant];
  const [hover, setHover] = useState(false);
  const active = open || hover;
  const cx = '50%';

  return (
    <div
      style={{ position: 'relative', width: BASE_WIDTH, height: BASE_HEIGHT, touchAction: 'manipulation', WebkitTapHighlightColor: 'transparent' }}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => { setHover(false); }}
    >
      <div style={{ position: 'absolute', top: cx, left: cx, width: BASE_WIDTH, height: BASE_HEIGHT, transform: 'translate(-50%,-50%)', perspective: 800 }}>
        {/* back panel */}
        <div style={{ position: 'absolute', top: cx, left: cx, width: BASE_WIDTH, height: BASE_HEIGHT, transform: 'translate(-50%,-50%)', borderRadius: 25, background: theme.backFill, border: `1px solid ${theme.flapStroke}`, opacity: 0.9 }} />

        {/* documents, centered via flex static position, fanned out */}
        <div style={{ position: 'absolute', top: cx, left: cx, transform: 'translate(-50%,-50%)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <motion.div style={{ position: 'absolute' }}
            animate={active ? { y: -40, x: 40, rotate: 11 } : { y: -8, x: 8, rotate: 6 }}
            transition={{ type: 'spring', stiffness: 130, damping: 15 }}>
            <DocumentCard theme={theme} />
          </motion.div>
          <motion.div style={{ position: 'absolute' }}
            animate={active ? { y: -52, x: 0, rotate: -1 } : { y: -16, x: 0, rotate: 0 }}
            transition={{ type: 'spring', stiffness: 130, damping: 15 }}>
            <DocumentCard theme={theme} />
          </motion.div>
          <motion.div style={{ position: 'absolute' }}
            animate={active ? { y: -40, x: -40, rotate: -10 } : { y: -8, x: -8, rotate: -5 }}
            transition={{ type: 'spring', stiffness: 130, damping: 15 }}>
            <DocumentCard theme={theme} />
          </motion.div>
        </div>

        {/* front flap: frosted glass, hinged at the bottom */}
        <motion.div
          style={{ position: 'absolute', top: cx, left: cx, width: BASE_WIDTH, height: 241, transform: 'translate(-50%,-50%)', marginTop: 14, transformOrigin: 'bottom center', transformStyle: 'preserve-3d' }}
          animate={{ rotateX: active ? -54 : -14 }}
          transition={{ type: 'spring', stiffness: 120, damping: 14 }}
        >
          <div
            style={{
              position: 'absolute', inset: 0, backdropFilter: 'blur(7px)', WebkitBackdropFilter: 'blur(7px)',
              clipPath: `path('${FLAP_PATH}')`, WebkitClipPath: `path('${FLAP_PATH}')`, transform: 'translateZ(0)', willChange: 'transform'
            }}
          />
          <svg width={BASE_WIDTH} height={241} viewBox="0 0 321 241" fill="none" style={{ position: 'absolute', inset: 0 }}>
            <path d={FLAP_PATH} fill={theme.flapFill} fillOpacity={theme.flapFillOpacity} />
            <path d={FLAP_PATH} stroke={theme.flapStroke} strokeWidth="1.3" />
          </svg>
        </motion.div>
      </div>
    </div>
  );
}
