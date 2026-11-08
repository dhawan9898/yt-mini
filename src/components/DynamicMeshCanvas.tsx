import React, { useEffect, useRef } from 'react';
import { usePlayer } from '../context/PlayerContext';

function hexToRgba(hex: string, alpha: number): string {
  let c = hex.replace('#', '');
  if (c.length === 3) {
    c = c.split('').map(x => x + x).join('');
  }
  const num = parseInt(c, 16);
  if (isNaN(num)) return `rgba(108,92,231,${alpha})`;
  return `rgba(${(num >> 16) & 255}, ${(num >> 8) & 255}, ${num & 255}, ${alpha})`;
}

export const DynamicMeshCanvas: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const { currentTrack } = usePlayer();
  const dominantColor = currentTrack.dominantColor || '#6c5ce7';
  const secondaryColor = currentTrack.secondaryColor || '#ff7675';

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationFrameId: number;
    let phase = 0;

    const resize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    resize();
    window.addEventListener('resize', resize);

    const render = () => {
      phase += 0.008;
      const w = canvas.width;
      const h = canvas.height;

      ctx.fillStyle = '#07070a';
      ctx.fillRect(0, 0, w, h);

      const x1 = w * (0.35 + 0.18 * Math.cos(phase));
      const y1 = h * (0.28 + 0.15 * Math.sin(phase));
      const r1 = Math.min(w, h) * 0.75;

      const x2 = w * (0.68 + 0.14 * Math.sin(phase * 0.8));
      const y2 = h * (0.65 + 0.18 * Math.cos(phase * 0.8));
      const r2 = Math.min(w, h) * 0.85;

      const grad1 = ctx.createRadialGradient(x1, y1, 0, x1, y1, r1);
      grad1.addColorStop(0, hexToRgba(dominantColor, 0.45));
      grad1.addColorStop(0.5, hexToRgba(dominantColor, 0.18));
      grad1.addColorStop(1, 'transparent');
      ctx.fillStyle = grad1;
      ctx.beginPath();
      ctx.arc(x1, y1, r1, 0, Math.PI * 2);
      ctx.fill();

      const grad2 = ctx.createRadialGradient(x2, y2, 0, x2, y2, r2);
      grad2.addColorStop(0, hexToRgba(secondaryColor, 0.4));
      grad2.addColorStop(0.5, hexToRgba(secondaryColor, 0.12));
      grad2.addColorStop(1, 'transparent');
      ctx.fillStyle = grad2;
      ctx.beginPath();
      ctx.arc(x2, y2, r2, 0, Math.PI * 2);
      ctx.fill();

      const vignette = ctx.createLinearGradient(0, 0, 0, h);
      vignette.addColorStop(0, 'rgba(0, 0, 0, 0.35)');
      vignette.addColorStop(0.5, 'transparent');
      vignette.addColorStop(1, 'rgba(0, 0, 0, 0.85)');
      ctx.fillStyle = vignette;
      ctx.fillRect(0, 0, w, h);

      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      window.removeEventListener('resize', resize);
      cancelAnimationFrame(animationFrameId);
    };
  }, [dominantColor, secondaryColor]);

  return <canvas ref={canvasRef} className="mesh-canvas" />;
};
