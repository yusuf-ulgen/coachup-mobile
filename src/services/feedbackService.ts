import { Colors } from '../theme/colors';

export type FeedbackVariant = 'success' | 'error' | 'warning' | 'info';

export interface FeedbackButton {
  text: string;
  style?: 'default' | 'cancel' | 'destructive';
  onPress?: () => void;
}

export interface FeedbackDialogOptions {
  title: string;
  message?: string;
  variant?: FeedbackVariant;
  buttons?: FeedbackButton[];
}

export interface ToastOptions {
  message: string;
  title?: string;
  variant?: FeedbackVariant;
  durationMs?: number;
}

export interface InternalFeedbackState {
  dialog: FeedbackDialogOptions | null;
  toast: ToastOptions | null;
}

type FeedbackListener = (state: InternalFeedbackState) => void;

class FeedbackManager {
  private listeners: Set<FeedbackListener> = new Set();
  private currentState: InternalFeedbackState = {
    dialog: null,
    toast: null,
  };
  private toastTimer: any = null;

  public subscribe(listener: FeedbackListener) {
    this.listeners.add(listener);
    listener(this.currentState);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    this.listeners.forEach((listener) => listener({ ...this.currentState }));
  }

  public showDialog(options: FeedbackDialogOptions) {
    this.currentState.dialog = options;
    this.notify();
  }

  public hideDialog() {
    this.currentState.dialog = null;
    this.notify();
  }

  public showToast(options: ToastOptions) {
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
      this.toastTimer = null;
    }

    this.currentState.toast = options;
    this.notify();

    const duration = options.durationMs || 3000;
    this.toastTimer = setTimeout(() => {
      this.hideToast();
    }, duration);
  }

  public hideToast() {
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
      this.toastTimer = null;
    }
    this.currentState.toast = null;
    this.notify();
  }

  // ── Sanitization Helper ───────────────────────────────────────────────────
  public sanitizeError(error: any, fallbackMessage: string = 'İşlem gerçekleştirilemedi. Lütfen tekrar deneyin.'): string {
    if (!error) return fallbackMessage;

    // Log original technical error for developers
    console.error('[FeedbackService Sanitized Error]:', error);

    const errorStr = typeof error === 'string' 
      ? error 
      : error?.message || error?.error_description || JSON.stringify(error);

    const lower = errorStr.toLowerCase();

    // Catch technical DB / Schema / Internal errors
    if (
      lower.includes('column') ||
      lower.includes('schema') ||
      lower.includes('relation') ||
      lower.includes('postgres') ||
      lower.includes('supabase') ||
      lower.includes('syntax error') ||
      lower.includes('foreign key') ||
      lower.includes('violates') ||
      lower.includes('duplicate key') ||
      lower.includes('jwt') ||
      lower.includes('token')
    ) {
      return fallbackMessage;
    }

    if (lower.includes('network') || lower.includes('fetch') || lower.includes('timeout')) {
      return 'İnternet bağlantısı kurulamadı. Lütfen bağlantınızı kontrol edin.';
    }

    if (lower.includes('invalid login credentials') || lower.includes('invalid_grant')) {
      return 'E-posta veya şifre hatalı.';
    }

    if (lower.includes('user already registered') || lower.includes('already exists')) {
      return 'Bu e-posta adresi zaten kayıtlı.';
    }

    // Return human message if standard string without schema code
    if (typeof error === 'string' && error.length < 120 && !error.includes('{') && !error.includes('_')) {
      return error;
    }

    if (error?.message && typeof error.message === 'string' && error.message.length < 120 && !error.message.includes('column') && !error.message.includes('cache')) {
      return error.message;
    }

    return fallbackMessage;
  }

  // ── High-Level Convenience APIs ──────────────────────────────────────────

  public show(options: FeedbackDialogOptions) {
    this.showDialog(options);
  }

  public success(options: { title?: string; message: string }) {
    this.showToast({
      title: options.title || 'Başarılı',
      message: options.message,
      variant: 'success',
    });
  }

  public toast(message: string, variant: FeedbackVariant = 'success', title?: string) {
    this.showToast({
      title: title || (variant === 'success' ? 'Başarılı' : variant === 'error' ? 'Hata' : 'Bilgi'),
      message,
      variant,
    });
  }

  public error(options: { title?: string; message: any; fallbackMessage?: string }) {
    const safeMsg = this.sanitizeError(options.message, options.fallbackMessage || 'Bir hata oluştu. Lütfen tekrar deneyin.');
    this.showDialog({
      title: options.title || 'Hata',
      message: safeMsg,
      variant: 'error',
      buttons: [{ text: 'Tamam', style: 'default' }],
    });
  }

  public warning(options: { title?: string; message: string }) {
    this.showDialog({
      title: options.title || 'Uyarı',
      message: options.message,
      variant: 'warning',
      buttons: [{ text: 'Tamam', style: 'default' }],
    });
  }

  public info(options: { title?: string; message: string }) {
    this.showDialog({
      title: options.title || 'Bilgi',
      message: options.message,
      variant: 'info',
      buttons: [{ text: 'Tamam', style: 'default' }],
    });
  }

  public confirm(options: {
    title: string;
    message: string;
    confirmText?: string;
    cancelText?: string;
    destructive?: boolean;
  }): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      this.showDialog({
        title: options.title,
        message: options.message,
        variant: options.destructive ? 'error' : 'info',
        buttons: [
          {
            text: options.cancelText || 'Vazgeç',
            style: 'cancel',
            onPress: () => resolve(false),
          },
          {
            text: options.confirmText || (options.destructive ? 'Sil' : 'Tamam'),
            style: options.destructive ? 'destructive' : 'default',
            onPress: () => resolve(true),
          },
        ],
      });
    });
  }

  public destructive(options: {
    title: string;
    message: string;
    confirmText?: string;
    cancelText?: string;
  }): Promise<boolean> {
    return this.confirm({
      ...options,
      destructive: true,
    });
  }
}

export const feedback = new FeedbackManager();
