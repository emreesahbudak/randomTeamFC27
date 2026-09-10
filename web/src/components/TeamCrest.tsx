interface TeamCrestProps {
  code?: string;
  colorHex?: string | null;
  size?: "sm" | "lg";
  spinning?: boolean;
}

/** CSS-drawn crest (no image assets) — matches the FC27 UI prototype's placeholder art. */
export function TeamCrest({ code, colorHex, size = "lg", spinning = false }: TeamCrestProps) {
  const dimensions = size === "lg" ? "h-[70px] w-[70px] text-lg" : "h-11 w-11 text-[11px]";

  if (!code) {
    return (
      <div
        className={`crest flex ${dimensions} items-center justify-center bg-surface-3 font-display font-bold text-text-faint ring-1 ring-inset ring-border`}
      >
        ?
      </div>
    );
  }

  return (
    <div
      className={`crest flex ${dimensions} items-center justify-center font-display font-bold text-white shadow-md ${
        spinning ? "animate-pulse" : ""
      }`}
      style={{ background: colorHex ?? "#54625c" }}
    >
      {code.slice(0, 3)}
    </div>
  );
}
