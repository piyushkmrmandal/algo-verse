import { describe, it, expect } from 'vitest';
import { api, getErrorMessage, isApiError } from '../api';
import axios from 'axios';

describe('api axios instance', () => {
  it('has correct baseURL', () => {
    expect(api.defaults.baseURL).toBe('/api/v1');
  });

  it('has correct Content-Type header', () => {
    expect(api.defaults.headers['Content-Type']).toBe('application/json');
  });

  it('has correct timeout', () => {
    expect(api.defaults.timeout).toBe(30_000);
  });
});

describe('getErrorMessage', () => {
  it('extracts message from err.response.data.message when isApiError', () => {
    // Create an axios error with the ApiError shape
    const axiosError = Object.assign(new Error('Request failed'), {
      isAxiosError: true,
      response: {
        data: {
          timestamp: '2024-01-01T00:00:00Z',
          status: 400,
          error: 'Bad Request',
          message: 'Email already exists',
          path: '/api/v1/auth/register',
        },
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config: {},
      },
    });

    const result = getErrorMessage(axiosError);
    expect(result).toBe('Email already exists');
  });

  it('falls back to err.message when no response data', () => {
    const error = new Error('Network Error');
    const result = getErrorMessage(error);
    expect(result).toBe('Network Error');
  });

  it('falls back to "An unexpected error occurred" for unknown errors', () => {
    const result = getErrorMessage('some string error');
    expect(result).toBe('An unexpected error occurred');
  });

  it('falls back to "An unexpected error occurred" for null', () => {
    const result = getErrorMessage(null);
    expect(result).toBe('An unexpected error occurred');
  });

  it('falls back to err.message for plain Error without axios response', () => {
    const axiosError = Object.assign(new Error('timeout of 30000ms exceeded'), {
      isAxiosError: true,
      response: undefined,
    });
    // No response.data.status, so isApiError returns false
    const result = getErrorMessage(axiosError);
    expect(result).toBe('timeout of 30000ms exceeded');
  });
});

describe('isApiError', () => {
  it('returns true for axios error with data.status', () => {
    const axiosError = Object.assign(new Error('Request failed'), {
      isAxiosError: true,
      response: {
        data: {
          status: 404,
          message: 'Not found',
          error: 'Not Found',
          timestamp: '',
          path: '',
        },
        status: 404,
        statusText: 'Not Found',
        headers: {},
        config: {},
      },
    });
    expect(isApiError(axiosError)).toBe(true);
  });

  it('returns false for non-axios errors', () => {
    const error = new Error('plain error');
    expect(isApiError(error)).toBe(false);
  });

  it('returns false for axios error without response', () => {
    const axiosError = Object.assign(new Error('Network Error'), {
      isAxiosError: true,
      response: undefined,
    });
    expect(isApiError(axiosError)).toBe(false);
  });
});
