import { ErrorHandler } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { DeferredErrorHandler, errorTrackingOptions, SENTRY_DSN } from './app/error-tracking';

// Error tracking, only on the live site: Angular hands every uncaught error to its ErrorHandler, which here also
// reports it to Sentry (loaded in the background).
const errorTracking = errorTrackingOptions(SENTRY_DSN, window.location.hostname);
const reporting = errorTracking
  ? [{
      provide: ErrorHandler,
      useValue: new DeferredErrorHandler(import('./app/sentry-reporter').then((m) => m.startReporting(errorTracking))),
    }]
  : [];

bootstrapApplication(App, { providers: [...appConfig.providers, ...reporting] })
  .catch((err) => console.error(err));
