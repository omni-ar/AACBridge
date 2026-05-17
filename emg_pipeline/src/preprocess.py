"""
preprocess.py
EMG signal preprocessing for NinaPro DB5.

Pipeline (per channel):
  1. Bandpass filter: 20–450 Hz, 4th-order Butterworth
  2. Full-wave rectification: abs(signal)
  3. Window segmentation: 200 ms windows (400 samples at 2000 Hz), no overlap

Output tensor: (N_windows, 400, 16)  float32
Label vector:  (N_windows,)           int64  — dominant stimulus per window
"""

import numpy as np
from scipy.signal import butter, filtfilt
from scipy.io import loadmat
from pathlib import Path
from typing import Tuple


# ── Constants ────────────────────────────────────────────────────────────────
FS = 2000           # NinaPro DB5 sampling frequency (Hz)
LOWCUT = 20         # bandpass low edge (Hz)
HIGHCUT = 450       # bandpass high edge (Hz)
FILTER_ORDER = 4
WINDOW_MS = 200     # window duration
WINDOW_SAMPLES = int(FS * WINDOW_MS / 1000)   # 400 samples
N_CHANNELS = 16


# ── Filtering ────────────────────────────────────────────────────────────────

def bandpass_filter(signal: np.ndarray,
                    lowcut: float = LOWCUT,
                    highcut: float = HIGHCUT,
                    fs: int = FS,
                    order: int = FILTER_ORDER) -> np.ndarray:
    """
    Apply zero-phase Butterworth bandpass filter to a 1-D signal.

    Args:
        signal: (N,) raw EMG samples
        lowcut:  lower cutoff in Hz
        highcut: upper cutoff in Hz
        fs:      sampling frequency in Hz
        order:   filter order

    Returns:
        filtered: (N,) filtered signal, same dtype as input
    """
    nyquist = 0.5 * fs
    low = lowcut / nyquist
    high = highcut / nyquist
    b, a = butter(order, [low, high], btype='band')
    return filtfilt(b, a, signal).astype(signal.dtype)


def bandpass_filter_multichannel(emg: np.ndarray) -> np.ndarray:
    """
    Apply bandpass filter to all channels independently.

    Args:
        emg: (N_samples, N_channels)

    Returns:
        filtered: (N_samples, N_channels)
    """
    out = np.empty_like(emg, dtype=np.float32)
    for ch in range(emg.shape[1]):
        out[:, ch] = bandpass_filter(emg[:, ch].astype(np.float64)).astype(np.float32)
    return out


# ── Rectification ────────────────────────────────────────────────────────────

def rectify(emg: np.ndarray) -> np.ndarray:
    """Full-wave rectification: abs(emg)."""
    return np.abs(emg)


# ── Windowing ────────────────────────────────────────────────────────────────

def segment_windows(emg: np.ndarray,
                    stimulus: np.ndarray,
                    window_samples: int = WINDOW_SAMPLES
                    ) -> Tuple[np.ndarray, np.ndarray]:
    """
    Segment processed EMG into non-overlapping windows and assign labels.

    Label per window = dominant (mode) stimulus value within that window.
    Partial trailing window is discarded.

    Args:
        emg:      (N_samples, N_channels) preprocessed float32
        stimulus: (N_samples,) or (N_samples, 1) integer gesture labels
        window_samples: samples per window (default 400 = 200 ms @ 2kHz)

    Returns:
        windows: (N_windows, window_samples, N_channels)  float32
        labels:  (N_windows,)                              int64
    """
    stim = stimulus.flatten().astype(np.int64)
    n_samples = emg.shape[0]
    n_windows = n_samples // window_samples

    windows = np.empty((n_windows, window_samples, emg.shape[1]), dtype=np.float32)
    labels = np.empty(n_windows, dtype=np.int64)

    for i in range(n_windows):
        start = i * window_samples
        end = start + window_samples
        windows[i] = emg[start:end]
        labels[i] = np.bincount(stim[start:end]).argmax()

    return windows, labels


# ── Full pipeline ─────────────────────────────────────────────────────────────

def preprocess_mat(mat_path: str | Path) -> Tuple[np.ndarray, np.ndarray]:
    """
    Load a NinaPro DB5 .mat file and run the full preprocessing pipeline.

    Args:
        mat_path: path to S{n}_E{m}_A1.mat

    Returns:
        windows: (N_windows, 400, 16)  float32
        labels:  (N_windows,)           int64
    """
    mat_path = Path(mat_path)
    data = loadmat(str(mat_path))

    emg = data['emg'].astype(np.float32)            # (N, 16)
    stimulus = data['stimulus'].flatten()            # (N,)

    emg_filtered = bandpass_filter_multichannel(emg)
    emg_rectified = rectify(emg_filtered)

    windows, labels = segment_windows(emg_rectified, stimulus)
    return windows, labels


def normalize_windows(windows: np.ndarray) -> np.ndarray:
    """
    Per-channel z-score normalization across the dataset.

    Args:
        windows: (N, T, C)

    Returns:
        normalized: (N, T, C)  zero mean, unit variance per channel
    """
    # Compute stats over (N, T) for each channel C
    mean = windows.mean(axis=(0, 1), keepdims=True)   # (1, 1, C)
    std = windows.std(axis=(0, 1), keepdims=True) + 1e-8
    return (windows - mean) / std