import http.server
import socketserver
import mimetypes
import os
import sys

PORT = 3000

# Add MIME mapping for APK
mimetypes.add_type('application/vnd.android.package-archive', '.apk')

class RobustHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        # Intercept '/' or '/index.html' requests to inject the real server-side detected origin
        normalized_path = self.path.split('?')[0].split('#')[0]
        if normalized_path in ('', '/', '/index.html'):
            try:
                with open('index.html', 'r', encoding='utf-8') as f:
                    content = f.read()
                
                # Determine protocol and host
                proto = self.headers.get('X-Forwarded-Proto', 'https')
                host = self.headers.get('X-Forwarded-Host')
                if not host:
                    host = self.headers.get('Host', 'localhost:3000')
                
                real_origin = f"{proto}://{host}"
                
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
                self.send_header('Access-Control-Allow-Origin', '*')
                self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0')
                self.end_headers()
                self.wfile.write(encoded_content)
                return
            except Exception as e:
                self.log_message("Error injecting origin: %s", str(e))
        
        super().do_GET()

    def end_headers(self):
        # Allow cross-origin requests and force download behavior
        self.send_header('Access-Control-Allow-Origin', '*')
        if self.path.endswith('.apk'):
            self.send_header('Content-Type', 'application/vnd.android.package-archive')
            self.send_header('Content-Disposition', 'attachment; filename="app-release.apk"')
            self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0')
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
    # Change working directory to / to serve project files
    os.chdir('/')
    server = ThreadingHTTPServer(('0.0.0.0', PORT), RobustHTTPRequestHandler)
    print(f"Starting robust multi-threaded server on port {PORT}...")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nKeyboard interrupt received, exiting.")
        sys.exit(0)
