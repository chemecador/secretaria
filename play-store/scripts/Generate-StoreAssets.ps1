param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

function ConvertTo-Color([string]$Hex) {
    [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function New-RoundedPath(
    [System.Drawing.RectangleF]$Rectangle,
    [float]$Radius
) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $Radius * 2
    $arc = [System.Drawing.RectangleF]::new(
        $Rectangle.X,
        $Rectangle.Y,
        $diameter,
        $diameter
    )
    $path.AddArc($arc, 180, 90)
    $arc.X = $Rectangle.Right - $diameter
    $path.AddArc($arc, 270, 90)
    $arc.Y = $Rectangle.Bottom - $diameter
    $path.AddArc($arc, 0, 90)
    $arc.X = $Rectangle.X
    $path.AddArc($arc, 90, 90)
    $path.CloseFigure()
    $path
}

function Set-HighQuality([System.Drawing.Graphics]$Graphics) {
    $Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $Graphics.InterpolationMode =
        [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $Graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $Graphics.TextRenderingHint =
        [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
}

function Save-StoreScreenshot(
    [string]$SourcePath,
    [string]$DestinationPath,
    [string]$Category,
    [string]$Headline,
    [string]$BackgroundHex,
    [string]$ForegroundHex
) {
    $source = [System.Drawing.Image]::FromFile($SourcePath)
    try {
        $bitmap = [System.Drawing.Bitmap]::new(
            1080,
            1920,
            [System.Drawing.Imaging.PixelFormat]::Format24bppRgb
        )
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                Set-HighQuality $graphics
                $background = ConvertTo-Color $BackgroundHex
                $foreground = ConvertTo-Color $ForegroundHex
                $graphics.Clear($background)

                $categoryFont = [System.Drawing.Font]::new(
                    "Segoe UI",
                    24,
                    [System.Drawing.FontStyle]::Bold,
                    [System.Drawing.GraphicsUnit]::Pixel
                )
                $headlineFont = [System.Drawing.Font]::new(
                    "Segoe UI",
                    58,
                    [System.Drawing.FontStyle]::Bold,
                    [System.Drawing.GraphicsUnit]::Pixel
                )
                $foregroundBrush = [System.Drawing.SolidBrush]::new($foreground)
                try {
                    $graphics.DrawString(
                        $Category.ToUpperInvariant(),
                        $categoryFont,
                        $foregroundBrush,
                        [System.Drawing.PointF]::new(62, 52)
                    )
                    $headlineFormat = [System.Drawing.StringFormat]::new()
                    $headlineFormat.Trimming = [System.Drawing.StringTrimming]::EllipsisWord
                    $graphics.DrawString(
                        $Headline,
                        $headlineFont,
                        $foregroundBrush,
                        [System.Drawing.RectangleF]::new(62, 105, 955, 190),
                        $headlineFormat
                    )
                    $headlineFormat.Dispose()
                } finally {
                    $foregroundBrush.Dispose()
                    $categoryFont.Dispose()
                    $headlineFont.Dispose()
                }

                $shadowRectangle = [System.Drawing.RectangleF]::new(64, 349, 952, 1531)
                $shadowPath = New-RoundedPath $shadowRectangle 42
                $shadowBrush = [System.Drawing.SolidBrush]::new(
                    [System.Drawing.Color]::FromArgb(48, 0, 0, 0)
                )
                try {
                    $graphics.FillPath($shadowBrush, $shadowPath)
                } finally {
                    $shadowBrush.Dispose()
                    $shadowPath.Dispose()
                }

                $screenRectangle = [System.Drawing.RectangleF]::new(58, 337, 964, 1531)
                $screenBounds = [System.Drawing.Rectangle]::new(58, 337, 964, 1531)
                $screenPath = New-RoundedPath $screenRectangle 42
                $oldClip = $graphics.Clip
                try {
                    $graphics.SetClip($screenPath)
                    $graphics.DrawImage(
                        $source,
                        $screenBounds,
                        0,
                        100,
                        1080,
                        1715,
                        [System.Drawing.GraphicsUnit]::Pixel
                    )
                } finally {
                    $graphics.Clip = $oldClip
                    $oldClip.Dispose()
                }

                $borderPen = [System.Drawing.Pen]::new(
                    [System.Drawing.Color]::FromArgb(78, $foreground),
                    2
                )
                try {
                    $graphics.DrawPath($borderPen, $screenPath)
                } finally {
                    $borderPen.Dispose()
                    $screenPath.Dispose()
                }
            } finally {
                $graphics.Dispose()
            }

            $destinationDirectory = Split-Path $DestinationPath -Parent
            New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
            $bitmap.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $bitmap.Dispose()
        }
    } finally {
        $source.Dispose()
    }
}

function Save-TabletScreenshot(
    [string]$SourcePath,
    [string]$DestinationPath
) {
    $source = [System.Drawing.Image]::FromFile($SourcePath)
    try {
        if ($source.Width -ne 2560 -or $source.Height -ne 1440) {
            throw "Tablet screenshot must be 2560x1440: $SourcePath"
        }

        $bitmap = [System.Drawing.Bitmap]::new(
            2560,
            1440,
            [System.Drawing.Imaging.PixelFormat]::Format24bppRgb
        )
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                Set-HighQuality $graphics
                $graphics.DrawImage($source, 0, 0, 2560, 1440)
            } finally {
                $graphics.Dispose()
            }

            $destinationDirectory = Split-Path $DestinationPath -Parent
            New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
            $bitmap.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $bitmap.Dispose()
        }
    } finally {
        $source.Dispose()
    }
}

function Save-FeatureGraphic(
    [string]$DestinationPath,
    [string]$Subtitle
) {
    $bitmap = [System.Drawing.Bitmap]::new(
        1024,
        500,
        [System.Drawing.Imaging.PixelFormat]::Format24bppRgb
    )
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            Set-HighQuality $graphics
            $graphics.Clear((ConvertTo-Color "#E7E1D7"))

            $accentBrush = [System.Drawing.SolidBrush]::new((ConvertTo-Color "#D9E2EE"))
            $greenBrush = [System.Drawing.SolidBrush]::new((ConvertTo-Color "#1B452A"))
            $creamBrush = [System.Drawing.SolidBrush]::new((ConvertTo-Color "#F5F1E8"))
            $blueBrush = [System.Drawing.SolidBrush]::new((ConvertTo-Color "#5D6F86"))
            $shadowBrush = [System.Drawing.SolidBrush]::new(
                [System.Drawing.Color]::FromArgb(28, 0, 0, 0)
            )
            try {
                $graphics.FillEllipse($accentBrush, -170, -220, 570, 570)
                $graphics.FillEllipse($greenBrush, -90, 365, 310, 310)

                $titleFont = [System.Drawing.Font]::new(
                    "Segoe UI",
                    66,
                    [System.Drawing.FontStyle]::Bold,
                    [System.Drawing.GraphicsUnit]::Pixel
                )
                $subtitleFont = [System.Drawing.Font]::new(
                    "Segoe UI",
                    29,
                    [System.Drawing.FontStyle]::Regular,
                    [System.Drawing.GraphicsUnit]::Pixel
                )
                try {
                    $graphics.DrawString(
                        "Secretaria",
                        $titleFont,
                        $greenBrush,
                        [System.Drawing.PointF]::new(58, 118)
                    )
                    $graphics.DrawString(
                        $Subtitle,
                        $subtitleFont,
                        $blueBrush,
                        [System.Drawing.RectangleF]::new(62, 215, 500, 145)
                    )
                } finally {
                    $titleFont.Dispose()
                    $subtitleFont.Dispose()
                }

                $backRectangle = [System.Drawing.RectangleF]::new(670, 65, 286, 352)
                $backPath = New-RoundedPath $backRectangle 34
                $graphics.FillPath($blueBrush, $backPath)
                $backPath.Dispose()

                $cardShadowRectangle = [System.Drawing.RectangleF]::new(595, 99, 330, 350)
                $cardShadowPath = New-RoundedPath $cardShadowRectangle 38
                $graphics.FillPath($shadowBrush, $cardShadowPath)
                $cardShadowPath.Dispose()

                $cardRectangle = [System.Drawing.RectangleF]::new(583, 87, 330, 350)
                $cardPath = New-RoundedPath $cardRectangle 38
                $graphics.FillPath($creamBrush, $cardPath)
                $cardPath.Dispose()

                $headerRectangle = [System.Drawing.RectangleF]::new(583, 87, 330, 84)
                $headerPath = New-RoundedPath $headerRectangle 38
                $graphics.FillPath($greenBrush, $headerPath)
                $graphics.FillRectangle($greenBrush, 583, 130, 330, 42)
                $headerPath.Dispose()

                foreach ($y in @(225, 292, 359)) {
                    $graphics.FillEllipse($greenBrush, 625, $y - 12, 24, 24)
                    $graphics.FillRectangle($blueBrush, 678, $y - 7, 178, 14)
                }
            } finally {
                $accentBrush.Dispose()
                $greenBrush.Dispose()
                $creamBrush.Dispose()
                $blueBrush.Dispose()
                $shadowBrush.Dispose()
            }
        } finally {
            $graphics.Dispose()
        }

        New-Item -ItemType Directory -Force -Path (Split-Path $DestinationPath -Parent) |
            Out-Null
        $bitmap.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

$locales = @(
    @{
        Code = "es-ES"
        Screens = @(
            @{ File = "01-lists"; Category = "Listas"; Headline = "Organiza tus planes`nen un solo lugar"; Background = "#1B452A"; Foreground = "#F5F1E8" },
            @{ File = "02-notes"; Category = "Notas"; Headline = "Notas visuales,`nclaras y flexibles"; Background = "#E9DDD1"; Foreground = "#243548" },
            @{ File = "03-reminders"; Category = "Recordatorios"; Headline = "No vuelvas a olvidar`nuna tarea"; Background = "#D9E2EE"; Foreground = "#243548" },
            @{ File = "04-reminder-dialog"; Category = "Avisos"; Headline = "Programa cada aviso`ncon fecha y hora"; Background = "#1B452A"; Foreground = "#F5F1E8" },
            @{ File = "05-friends"; Category = "Amigos"; Headline = "Organ$([char]0xED)zate tambi$([char]0xE9)n`nen compa$([char]0xF1)$([char]0xED)a"; Background = "#E1E7EF"; Foreground = "#243548" },
            @{ File = "06-shared"; Category = "Compartir"; Headline = "Comparte listas`nsin complicaciones"; Background = "#E9DDD1"; Foreground = "#243548" }
        )
        FeatureSubtitle = "Listas, notas y recordatorios`npara tenerlo todo bajo control."
    },
    @{
        Code = "en-US"
        Screens = @(
            @{ File = "01-lists"; Category = "Lists"; Headline = "Keep every plan`nin one place"; Background = "#1B452A"; Foreground = "#F5F1E8" },
            @{ File = "02-notes"; Category = "Notes"; Headline = "Visual notes that`nstay flexible"; Background = "#E9DDD1"; Foreground = "#243548" },
            @{ File = "03-reminders"; Category = "Reminders"; Headline = "Never lose track`nof a task"; Background = "#D9E2EE"; Foreground = "#243548" },
            @{ File = "04-reminder-dialog"; Category = "Alerts"; Headline = "Schedule every alert`nwith date and time"; Background = "#1B452A"; Foreground = "#F5F1E8" },
            @{ File = "05-friends"; Category = "Friends"; Headline = "Get organized`ntogether"; Background = "#E1E7EF"; Foreground = "#243548" },
            @{ File = "06-shared"; Category = "Sharing"; Headline = "Share lists`nwithout the hassle"; Background = "#E9DDD1"; Foreground = "#243548" }
        )
        FeatureSubtitle = "Lists, notes and reminders`nto keep everything under control."
    }
)

foreach ($locale in $locales) {
    foreach ($screen in $locale.Screens) {
        Save-StoreScreenshot `
            -SourcePath (Join-Path $ProjectRoot "play-store\screenshots\raw\$($locale.Code)\$($screen.File).png") `
            -DestinationPath (Join-Path $ProjectRoot "play-store\screenshots\$($locale.Code)\$($screen.File).png") `
            -Category $screen.Category `
            -Headline $screen.Headline `
            -BackgroundHex $screen.Background `
            -ForegroundHex $screen.Foreground

        Save-TabletScreenshot `
            -SourcePath (Join-Path $ProjectRoot "play-store\screenshots\tablet\raw\$($locale.Code)\$($screen.File).png") `
            -DestinationPath (Join-Path $ProjectRoot "play-store\screenshots\tablet\$($locale.Code)\tablet-$($locale.Code)-$($screen.File).png")
    }

    Save-FeatureGraphic `
        -DestinationPath (Join-Path $ProjectRoot "play-store\graphics\$($locale.Code)\feature-graphic.png") `
        -Subtitle $locale.FeatureSubtitle
}

Write-Host "Store assets generated under $ProjectRoot\play-store"
