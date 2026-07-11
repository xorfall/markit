import { useEffect, useRef } from 'react';
import { SearchIcon } from '../components/icons';

interface CommandBarProps {
  value: string;
  onChange: (value: string) => void;
}

/** The hero search command bar. ⌘K (Ctrl+K) focuses it; Escape clears it. */
export function CommandBar({ value, onChange }: CommandBarProps): JSX.Element {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        inputRef.current?.focus();
        inputRef.current?.select();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <div className="command-bar" onClick={() => inputRef.current?.focus()}>
      <span className="command-bar-icon">
        <SearchIcon width={16} height={16} />
      </span>
      <input
        ref={inputRef}
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') onChange('');
        }}
        placeholder="Search inside your links…"
        aria-label="Search inside your links"
        enterKeyHint="search"
      />
      <kbd className="kbd">⌘K</kbd>
    </div>
  );
}
