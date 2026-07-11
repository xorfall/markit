import { Component, type ErrorInfo, type ReactNode } from 'react';

interface State {
  error: Error | null;
}

/**
 * Catches render errors so a single broken component shows a recoverable message
 * instead of unmounting the whole app to a blank white page.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Surfaced to the console for debugging; a real deployment would report this.
    console.error('UI error boundary caught:', error, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.error) {
      return (
        <div className="center-fill" role="alert">
          <div className="modal" style={{ textAlign: 'center' }}>
            <h2 className="modal-title">Something broke on this screen</h2>
            <p style={{ color: 'var(--ink-soft)', margin: '0 0 var(--sp-4)' }}>
              Your data is safe. Reload to continue.
            </p>
            <button className="btn btn-primary" onClick={() => window.location.reload()}>
              Reload markit
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
