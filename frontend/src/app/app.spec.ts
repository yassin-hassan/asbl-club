import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { provideApi } from './api/generated';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideApi('')],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('offers a login link while logged out', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const nav = (fixture.nativeElement as HTMLElement).querySelector('nav');
    expect(nav?.textContent).toContain('Log in');
  });
});
