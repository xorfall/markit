import { useState, type FormEvent } from 'react';
import { PlusIcon } from '../components/icons';

interface InlineAddFormProps {
  triggerLabel: string;
  placeholder: string;
  submitLabel: string;
  mono?: boolean;
  onSubmit: (value: string) => void;
}

/** A quiet "+" trigger that expands into a single-field add form. */
export function InlineAddForm({
  triggerLabel,
  placeholder,
  submitLabel,
  mono = false,
  onSubmit,
}: InlineAddFormProps): JSX.Element {
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState('');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = value.trim();
    if (!trimmed) return;
    onSubmit(trimmed);
    setValue('');
    setOpen(false);
  };

  if (!open) {
    return (
      <button className="btn btn-sm btn-ghost" onClick={() => setOpen(true)}>
        <PlusIcon width={14} height={14} /> {triggerLabel}
      </button>
    );
  }

  return (
    <form onSubmit={submit} style={{ display: 'flex', gap: 'var(--sp-2)' }}>
      <input
        className={`input btn-sm${mono ? ' mono' : ''}`}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={placeholder}
        autoFocus
        onBlur={() => {
          if (!value.trim()) setOpen(false);
        }}
      />
      <button type="submit" className="btn btn-sm btn-primary">
        {submitLabel}
      </button>
    </form>
  );
}
