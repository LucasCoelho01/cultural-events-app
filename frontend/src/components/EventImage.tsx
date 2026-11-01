import { useState } from 'react';

type EventImageProps = {
  src?: string | null;
  title: string;
  className?: string;
};

/** 16:9 event image; falls back to a branded placeholder when absent or broken. */
export function EventImage({ src, title, className = '' }: EventImageProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const showImage = Boolean(src) && failedSrc !== src;

  return (
    <div
      className={`relative aspect-video w-full overflow-hidden bg-gray-100 ${className}`}
    >
      {showImage ? (
        <img
          src={src ?? undefined}
          alt={title}
          loading="lazy"
          onError={() => setFailedSrc(src ?? null)}
          className="h-full w-full object-cover"
        />
      ) : (
        <div
          data-testid="image-placeholder"
          className="flex h-full w-full items-center justify-center bg-gradient-to-br from-brand-purple via-brand-pink to-brand-orange"
        >
          <img
            src="/logo-mark.svg"
            alt=""
            aria-hidden="true"
            className="h-1/3 w-auto opacity-90"
          />
        </div>
      )}
    </div>
  );
}
