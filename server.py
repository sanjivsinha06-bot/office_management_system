import http.server
import socketserver
import mimetypes
import os
import sys

PORT = int(os.environ.get('PORT', 3000))

# Add MIME mapping for APK
mimetypes.add_type('application/vnd.android.package-archive', '.apk')

class RobustHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def do_HEAD(self):
        normalized_path = self.path.split('?')[0].split('#')[0]
        if normalized_path in ('/app-release.apk', '/app.apk') or normalized_path.endswith('.apk'):
            apk_path = 'app/build/outputs/apk/debug/app-debug.apk'
            if not os.path.exists(apk_path):
                for path in ['.build-outputs/app-debug.apk', 'app-release.apk', 'app.apk']:
                    if os.path.exists(path):
                        apk_path = path
                        break
            
            if os.path.exists(apk_path):
                self.send_response(200)
                self.send_header('Content-Type', 'application/vnd.android.package-archive')
                self.send_header('Content-Length', str(os.path.getsize(apk_path)))
                self.end_headers()
                return
            else:
                self.send_error(404, "APK not found")
                return
        elif normalized_path in ('', '/', '/index.html', '/version.json'):
            self.send_response(200)
            if normalized_path == '/version.json':
                self.send_header('Content-Type', 'application/json')
            else:
                self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            return
        super().do_HEAD()

    def do_GET(self):
        normalized_path = self.path.split('?')[0].split('#')[0]
        self.log_message("GET Request: %s | Headers: %s", normalized_path, str(self.headers).replace('\n', ' | '))

        # 1. Dynamically serve the latest compiled APK directly from build outputs
        if normalized_path in ('/app-release.apk', '/app.apk') or normalized_path.endswith('.apk'):
            self.log_message("APK request detected: %s", normalized_path)
            apk_path = 'app/build/outputs/apk/debug/app-debug.apk'
            if not os.path.exists(apk_path):
                # Fallbacks if debug output is missing
                for path in ['.build-outputs/app-debug.apk', 'app-release.apk', 'app.apk']:
                    if os.path.exists(path):
                        apk_path = path
                        break
            
            if not os.path.exists(apk_path):
                 self.log_message("CRITICAL: No APK found in any expected location!")
                 self.send_error(404, "APK not found on server")
                 return

            try:
                self.log_message("Serving APK from %s", apk_path)
                self.send_response(200)
                self.send_header('Content-Type', 'application/vnd.android.package-archive')
                self.send_header('Content-Length', str(os.path.getsize(apk_path)))
                self.end_headers()
                import shutil
                with open(apk_path, 'rb') as f:
                    shutil.copyfileobj(f, self.wfile)
                return
            except Exception as e:
                self.log_message("Error serving APK %s: %s", apk_path, str(e))
                return

        # 2. Prevent caching of version.json to ensure accurate update checks
        if normalized_path == '/version.json':
            try:
                version_path = 'version.json'
                if not os.path.exists(version_path):
                    # Try to find it in assets if not in root
                    version_path = 'app/src/main/assets/version.json'

                with open(version_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                encoded_content = content.encode('utf-8')
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(encoded_content)))
                self.end_headers()
                self.wfile.write(encoded_content)
                return
            except Exception as e:
                self.log_message("Error serving version.json: %s", str(e))
                self.send_error(500, "Internal server error")
                return

        # Intercept '/' or '/index.html' requests to inject the real server-side detected origin
        if normalized_path in ('', '/', '/index.html'):
            try:
                index_path = 'index.html'
                if not os.path.exists(index_path):
                    self.send_error(404, "index.html not found")
                    return

                with open(index_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                # Determine protocol and host
                self.log_message("Request Headers for /: %s", str(self.headers))
                proto = self.headers.get('X-Forwarded-Proto', 'https')
                host = self.headers.get('X-Forwarded-Host')
                if not host:
                    host = self.headers.get('Host', 'localhost:3000')
                
                real_origin = f"{proto}://{host}"
                self.log_message("Injected Origin: %s", real_origin)
                
                # Inject a script variable before </head> or at the top
                injection = f'<script>window.__INJECTED_ORIGIN__ = "{real_origin}";</script>'
                if '</head>' in content:
                    content = content.replace('</head>', f"{injection}</head>", 1)
                else:
                    content = injection + content
                
                encoded_content = content.encode('utf-8')
                
                self.send_response(200)
                self.send_header('Content-Type', 'text/html; charset=utf-8')
                self.send_header('Content-Length', str(len(encoded_content)))
                self.end_headers()
                self.wfile.write(encoded_content)
                return
            except Exception as e:
                self.log_message("Error injecting origin: %s", str(e))
                self.send_error(500, "Internal server error")
                return
        
        super().do_GET()

    def end_headers(self):
        # Allow cross-origin requests and force download behavior
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0')
        if self.path.split('?')[0].endswith('.apk'):
            self.send_header('Content-Disposition', 'attachment; filename="OfficeManagementSystem.apk"')
        super().end_headers()

    def handle_error(self, format, *args):
        # Suppress logging crashes to stderr
        pass

    def log_message(self, format, *args):
        # Custom logging to a file to monitor server activity without clogging stderr/stdout
        try:
            with open("server.log", "a") as log_file:
                log_file.write("%s - - [%s] %s\n" % (self.address_string(), self.log_date_time_string(), format%args))
        except Exception:
            pass

class ThreadingHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True

if __name__ == '__main__':
    # Change working directory to current directory to serve files
    server = ThreadingHTTPServer(('0.0.0.0', PORT), RobustHTTPRequestHandler)
    print(f"Starting robust multi-threaded server on port {PORT}...")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nKeyboard interrupt received, exiting.")
        sys.exit(0)
